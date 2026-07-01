package com.org.meeple.infra.teammatch.query

import com.org.meeple.common.coin.CoinUsageType
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.core.teammatch.query.dao.GetRecommendedTeamDao
import com.org.meeple.core.teammatch.query.dto.RecommendedTeam
import com.org.meeple.infra.teammatch.command.entity.QRecommendedTeamEntity
import com.org.meeple.infra.teammatch.command.entity.QTeamEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.querydsl.core.Tuple
import com.querydsl.core.types.dsl.StringExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetRecommendedTeamDao]의 QueryDSL 구현체. (조회 전용)
 * ① 추천 행(recommended_teams) ⋈ teams(ACTIVE)(⟕ regions) 헤더들을 최신순으로 조회(teams @SQLRestriction이 해체 팀 제외, 팀 활동지역명은 regions left join),
 * ② 그 팀들의 ACTIVE 구성원을 [RecommendedTeamMemberLoader]로 한 번에 조회해 팀별로 묶어 [RecommendedTeam] 리스트로 조립한다.
 * 추천이 없거나 팀이 모두 해체(soft delete/비ACTIVE)됐으면 빈 리스트.
 *
 * 인덱스: 헤더는 recommended_teams.user_id 동등 seek(ux_user_id) + teams PK 조인이라 풀스캔이 없다. (구성원 쪽 인덱스는 로더 참고)
 */
@Component
class GetRecommendedTeamDaoImpl(
	private val queryFactory: JPAQueryFactory,
	private val recommendedTeamMemberLoader: RecommendedTeamMemberLoader,
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
		val membersByTeamId: Map<Long, TeamMembers> = recommendedTeamMemberLoader.loadByTeamIds(teamIds)

		return headers.map { header: Tuple ->
			val teamId: Long = header.get(team.id)!!
			val teamMembers: TeamMembers? = membersByTeamId[teamId]
			RecommendedTeam(
				teamId = teamId,
				name = header.get(team.name)!!,
				introduction = header.get(team.introduction),
				activityArea = header.get(teamActivityArea),
				members = teamMembers?.members.orEmpty(),
				lastLoginAt = teamMembers?.lastLoginAt,
				// 순수 추천 팀은 아직 team_match가 없어, 관심 보낼 때 생성될 매칭의 기본 비용(MEETING_INIT/ACCEPT)을 채운다.
				datingInitAmount = CoinUsageType.MEETING_INIT.coinAmount,
				datingAcceptAmount = CoinUsageType.MEETING_ACCEPT.coinAmount,
				// 아직 매칭이 없으므로 팀 매칭 id/상태/관심 여부는 비어 있다.
				teamMatchId = null,
				teamMatchStatus = null,
				teamMatchExpiresAt = null,
				hasUserInterest = false,
				hasPartnerInterest = false,
			)
		}
	}
}
