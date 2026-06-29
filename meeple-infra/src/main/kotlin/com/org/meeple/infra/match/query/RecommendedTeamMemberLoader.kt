package com.org.meeple.infra.match.query

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.core.match.query.dto.RecommendedTeamMember
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.Tuple
import com.querydsl.core.types.ConstructorExpression
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * 팀 카드(추천 팀·매칭 팀)에 붙일 ACTIVE 구성원 프로필을 적재하는 공용 로더. (조회 전용)
 * 주어진 팀들의 구성원을 team_members ⋈ match_user ⋈ user_details(⟕ regions)로 한 번에(IN) 조회해 teamId별로 묶는다.
 * (닉네임·성별·프로필이미지·생일=match_user, 직업·회사명·키·자기소개·특성·관심사=user_details, 활동지역명=regions)
 *
 * 인덱스: team_members(team_id, user_id) 유니크(ux_team_id_user_id)의 선두 team_id로 IN seek + match_user·user_details user_id 유니크 조인 + regions PK 조인이라 N+1·풀스캔이 없다.
 */
@Component
class RecommendedTeamMemberLoader(
	private val queryFactory: JPAQueryFactory,
) {

	/** [teamIds] 팀들의 ACTIVE 구성원 프로필을 teamId별로 묶어 반환한다. (빈 입력이면 빈 맵) */
	fun loadByTeamIds(teamIds: List<Long>): Map<Long, List<RecommendedTeamMember>> {
		if (teamIds.isEmpty()) return emptyMap()

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
			userDetail.universityName,
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

		return queryFactory
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
	}
}
