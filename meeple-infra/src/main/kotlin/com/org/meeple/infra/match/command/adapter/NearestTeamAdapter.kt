package com.org.meeple.infra.match.command.adapter

import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.command.application.port.out.GetNearestTeamPort
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.region.RegionProximityRegistry
import com.querydsl.core.Tuple
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetNearestTeamPort]의 command 어댑터. (추천 적재용 — teamId만 찾는다)
 * match_user에서 유저의 성별·활동지역을 읽은 뒤, 권역 근접 순서([RegionProximityRegistry])대로 훑어
 * 반대 성별 결성(ACTIVE) 팀 1곳을 먼저 만나는 즉시 그 teamId를 반환한다. (지역 단위 단일 조회라 매 쿼리가 인덱스 seek)
 * 찾는 성별 팀이 없는 지역은 [TeamPopulatedRegionRegistry]로 미리 걸러 헛조회를 막는다.
 * (조회 경로의 카드 조립(GetRecommendedTeamDao)과 달리 팀원 프로필을 붙이지 않는다 — 적재엔 teamId만 필요)
 */
@Component
class NearestTeamAdapter(
	private val queryFactory: JPAQueryFactory,
	private val regionProximityRegistry: RegionProximityRegistry,
	private val teamPopulatedRegionRegistry: TeamPopulatedRegionRegistry,
) : GetNearestTeamPort {

	override fun findNearestTeamId(userId: Long): Long? {
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val profile: Tuple = queryFactory
			.select(matchUser.gender, matchUser.regionId)
			.from(matchUser)
			.where(matchUser.userId.eq(userId))
			.fetchOne() ?: return null
		val opponentGender: Gender = profile.get(matchUser.gender)!!.opposite()
		val regionId: Long = profile.get(matchUser.regionId)!!

		val team: QTeamEntity = QTeamEntity.teamEntity
		// 상대 성별 결성 팀이 있는 지역만 본다. (팀 없는 지역은 건너뛰어 지역 단위 헛조회를 막는다)
		val populatedNearby: List<Long> = regionProximityRegistry.nearbyRegionIds(regionId)
			.filter { id: Long -> teamPopulatedRegionRegistry.contains(opponentGender, id) }
		for (candidateRegionId: Long in populatedNearby) {
			val teamId: Long = queryFactory
				.select(team.id)
				.from(team)
				.where(
					team.gender.eq(opponentGender),
					team.regionId.eq(candidateRegionId),
					team.status.eq(TeamStatus.ACTIVE),
				)
				.limit(1)
				.fetchFirst() ?: continue
			return teamId
		}
		return null
	}
}
