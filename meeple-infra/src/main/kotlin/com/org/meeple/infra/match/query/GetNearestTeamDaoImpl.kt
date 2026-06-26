package com.org.meeple.infra.match.query

import com.org.meeple.common.coin.CoinUsageType
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dao.GetNearestTeamDao
import com.org.meeple.core.match.query.dto.RecommendedTeam
import com.org.meeple.core.match.query.dto.RecommendedTeamMember
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.region.RegionProximityRegistry
import com.org.meeple.infra.region.entity.QRegionEntity
import com.querydsl.core.Tuple
import com.querydsl.core.types.dsl.StringExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetNearestTeamDao]의 QueryDSL 구현체. (조회 전용)
 * match_user에서 유저의 성별·활동지역을 읽은 뒤, 권역 근접 순서([RegionProximityRegistry])대로 훑어
 * 반대 성별 결성(ACTIVE) 팀 1곳을 먼저 만나는 즉시 [RecommendedTeamMemberLoader]로 팀원 프로필을 붙여 카드로 반환한다.
 * 카드의 비용·매칭 필드는 순수 추천과 동일(MEETING 기본 비용, teamMatch 관련은 null/false). 어디에도 없으면 null.
 */
@Component
class GetNearestTeamDaoImpl(
	private val queryFactory: JPAQueryFactory,
	private val regionProximityRegistry: RegionProximityRegistry,
	private val recommendedTeamMemberLoader: RecommendedTeamMemberLoader,
) : GetNearestTeamDao {

	override fun findNearestTeam(userId: Long): RecommendedTeam? {
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val profile: Tuple = queryFactory
			.select(matchUser.gender, matchUser.regionId)
			.from(matchUser)
			.where(matchUser.userId.eq(userId))
			.fetchOne() ?: return null
		val opponentGender: Gender = profile.get(matchUser.gender)!!.opposite()
		val regionId: Long = profile.get(matchUser.regionId)!!

		val team: QTeamEntity = QTeamEntity.teamEntity
		val teamRegion: QRegionEntity = QRegionEntity("teamRegion")
		// 팀 활동지역은 teams.region_id를 regions에 join해 "시/도 시/군/구"로 만든다. (지역 미설정이면 null)
		val teamActivityArea: StringExpression = teamRegion.sido.concat(" ").concat(teamRegion.sigungu)

		for (candidateRegionId: Long in regionProximityRegistry.nearbyRegionIds(regionId)) {
			val header: Tuple = queryFactory
				.select(team.id, team.name, team.introduction, teamActivityArea)
				.from(team)
				.leftJoin(teamRegion).on(teamRegion.id.eq(team.regionId))
				.where(
					team.gender.eq(opponentGender),
					team.regionId.eq(candidateRegionId),
					team.status.eq(TeamStatus.ACTIVE),
				)
				.limit(1)
				.fetchFirst() ?: continue

			val teamId: Long = header.get(team.id)!!
			val members: List<RecommendedTeamMember> =
				recommendedTeamMemberLoader.loadByTeamIds(listOf(teamId))[teamId].orEmpty()
			return RecommendedTeam(
				teamId = teamId,
				name = header.get(team.name)!!,
				introduction = header.get(team.introduction),
				activityArea = header.get(teamActivityArea),
				members = members,
				// 순수 추천 팀은 아직 team_match가 없어, 관심 보낼 때 생성될 매칭의 기본 비용(MEETING_INIT/ACCEPT)을 채운다.
				datingInitAmount = CoinUsageType.MEETING_INIT.coinAmount,
				datingAcceptAmount = CoinUsageType.MEETING_ACCEPT.coinAmount,
				// 아직 매칭이 없으므로 팀 매칭 id/상태/관심 여부는 비어 있다.
				teamMatchId = null,
				teamMatchStatus = null,
				hasUserInterest = false,
				hasPartnerInterest = false,
			)
		}
		return null
	}
}
