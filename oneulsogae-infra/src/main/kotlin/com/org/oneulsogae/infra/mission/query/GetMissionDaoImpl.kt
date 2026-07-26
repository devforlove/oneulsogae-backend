package com.org.oneulsogae.infra.mission.query

import com.org.oneulsogae.core.mission.query.dao.GetMissionDao
import com.org.oneulsogae.core.mission.query.dto.Mission
import com.org.oneulsogae.infra.mission.command.entity.QMissionEntity
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetMissionDao]의 QueryDSL 구현. (조회 전용)
 * 엔티티를 거치지 않고 [Mission] read model로 바로 투영한다. @SQLRestriction으로 soft-delete 행은 제외되고, active 조건을 where에서 건다.
 */
@Component
class GetMissionDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetMissionDao {

	override fun findActiveMissions(): List<Mission> {
		val mission: QMissionEntity = QMissionEntity.missionEntity
		return queryFactory
			.select(projection(mission))
			.from(mission)
			.where(mission.active.isTrue)
			.orderBy(mission.displayOrder.asc())
			.fetch()
	}

	override fun findActiveById(missionId: Long): Mission? {
		val mission: QMissionEntity = QMissionEntity.missionEntity
		return queryFactory
			.select(projection(mission))
			.from(mission)
			.where(mission.id.eq(missionId), mission.active.isTrue)
			.fetchOne()
	}

	private fun projection(mission: QMissionEntity): Expression<Mission> =
		Projections.constructor(
			Mission::class.java,
			mission.id,
			mission.type,
			mission.rewardCoin,
			mission.title,
			mission.description,
			mission.displayOrder,
		)
}
