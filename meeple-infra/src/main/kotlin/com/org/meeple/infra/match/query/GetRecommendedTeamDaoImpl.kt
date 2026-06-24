package com.org.meeple.infra.match.query

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.core.match.query.dao.GetRecommendedTeamDao
import com.org.meeple.core.match.query.dto.RecommendedTeam
import com.org.meeple.core.match.query.dto.RecommendedTeamMember
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QRecommendedTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.Tuple
import com.querydsl.core.types.ConstructorExpression
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.StringExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetRecommendedTeamDao]의 QueryDSL 구현체. (조회 전용)
 * ① 추천 행(recommended_teams) ⋈ teams(ACTIVE)(⟕ regions) 헤더들을 최신순으로 조회(teams @SQLRestriction이 해체 팀 제외, 팀 활동지역명은 regions left join),
 * ② 그 팀들의 ACTIVE 구성원을 team_members ⋈ match_user ⋈ user_details(⟕ regions)로 한 번에(IN) 조회해 팀별로 묶어 [RecommendedTeam] 리스트로 조립한다.
 *    (닉네임·성별·프로필이미지·생일=match_user, 직업·회사명·키·자기소개·특성·관심사=user_details, 활동지역명=regions)
 * 추천이 없거나 팀이 모두 해체(soft delete/비ACTIVE)됐으면 빈 리스트.
 *
 * 인덱스: ① 헤더는 recommended_teams.user_id 동등 seek(ux_user_id) + teams PK 조인이라 풀스캔이 없다.
 * ② 구성원은 team_members(team_id, user_id) 유니크(ux_team_id_user_id)의 선두 team_id로 IN seek + match_user·user_details user_id 유니크 조인 + regions PK 조인이라 N+1·풀스캔이 없다.
 */
@Component
class GetRecommendedTeamDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetRecommendedTeamDao {

	override fun findByUserId(userId: Long): List<RecommendedTeam> {
		val recommended: QRecommendedTeamEntity = QRecommendedTeamEntity.recommendedTeamEntity
		val team: QTeamEntity = QTeamEntity.teamEntity
		val teamRegion: QRegionEntity = QRegionEntity("teamRegion")

		// 팀 활동지역은 teams.region_id를 regions에 join해 "시/도 시/군/구"로 만든다. (지역 미설정이면 null)
		val teamActivityArea: StringExpression = teamRegion.sido.concat(" ").concat(teamRegion.sigungu)

		val headers: List<Tuple> = queryFactory
			.select(team.id, team.name, team.introduction, teamActivityArea)
			.from(recommended)
			.join(team).on(team.id.eq(recommended.teamId))
			.leftJoin(teamRegion).on(teamRegion.id.eq(team.regionId))
			.where(
				recommended.userId.eq(userId),
				team.status.eq(TeamStatus.ACTIVE),
			)
			.orderBy(recommended.recommendedDate.desc(), team.id.desc())
			.fetch()

		if (headers.isEmpty()) return emptyList()

		val teamIds: List<Long> = headers.map { header: Tuple -> header.get(team.id)!! }

		val member: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val userDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val region: QRegionEntity = QRegionEntity.regionEntity

		// 닉네임·성별·프로필이미지·생일=match_user, 직업·회사명·키·자기소개·특성·관심사=user_details, 활동지역=regions join.
		// (traits/interests는 @Convert(JSON) 컬럼이라 메타모델 ListPath로 select하면 컨버터가 안 먹어 Expressions.path로 기본 경로 참조)
		val memberProjection: ConstructorExpression<RecommendedTeamMember> = Projections.constructor(
			RecommendedTeamMember::class.java,
			member.userId,
			matchUser.nickname,
			userDetail.job,
			userDetail.companyName,
			matchUser.gender,
			matchUser.profileImageCode,
			matchUser.birthday,
			userDetail.height,
			// 표시용 활동지역은 regions를 join해 "시/도 시/군/구"로 만든다. (지역 미설정이면 null)
			region.sido.concat(" ").concat(region.sigungu),
			userDetail.introduction,
			Expressions.path(List::class.java, userDetail, "traits"),
			Expressions.path(List::class.java, userDetail, "interests"),
		)

		val membersByTeamId: Map<Long, List<RecommendedTeamMember>> = queryFactory
			.select(member.teamId, memberProjection)
			.from(member)
			.join(matchUser).on(matchUser.userId.eq(member.userId))
			.join(userDetail).on(userDetail.userId.eq(member.userId))
			.leftJoin(region).on(region.id.eq(userDetail.regionId))
			.where(
				member.teamId.`in`(teamIds),
				member.status.eq(TeamMemberStatus.ACTIVE),
			)
			.orderBy(member.userId.asc())
			.fetch()
			.groupBy({ row: Tuple -> row.get(member.teamId)!! }, { row: Tuple -> row.get(memberProjection)!! })

		return headers.map { header: Tuple ->
			val teamId: Long = header.get(team.id)!!
			RecommendedTeam(
				teamId = teamId,
				name = header.get(team.name)!!,
				introduction = header.get(team.introduction),
				activityArea = header.get(teamActivityArea),
				members = membersByTeamId[teamId].orEmpty(),
			)
		}
	}
}
