package com.org.meeple.infra.match.query

import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dao.GetNearestTeamDao
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.region.RegionProximityRegistry
import com.querydsl.core.Tuple
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetNearestTeamDao]의 QueryDSL 구현체. (조회 전용)
 * match_user에서 유저의 성별·활동지역을 읽은 뒤, 권역 근접 순서([RegionProximityRegistry])대로 훑어
 * 반대 성별 결성(ACTIVE) 팀 1곳을 먼저 만나는 즉시 그 id를 반환한다. (권역별 1건만 조회) 어디에도 없으면 null.
 */
@Component
class GetNearestTeamDaoImpl(
	private val queryFactory: JPAQueryFactory,
	private val regionProximityRegistry: RegionProximityRegistry,
) : GetNearestTeamDao {

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
		for (candidateRegionId: Long in regionProximityRegistry.nearbyRegionIds(regionId)) {
			val teamId: Long? = queryFactory
				.select(team.id)
				.from(team)
				.where(
					team.gender.eq(opponentGender),
					team.regionId.eq(candidateRegionId),
					team.status.eq(TeamStatus.ACTIVE),
				)
				.limit(1)
				.fetchFirst()
			if (teamId != null) return teamId
		}
		return null
	}
}
