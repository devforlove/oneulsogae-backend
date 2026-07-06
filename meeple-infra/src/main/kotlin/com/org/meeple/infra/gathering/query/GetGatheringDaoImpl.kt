package com.org.meeple.infra.gathering.query

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.core.gathering.query.dao.GetGatheringDao
import com.org.meeple.core.gathering.query.dto.GatheringView
import com.org.meeple.core.gathering.query.dto.GatheringViews
import com.org.meeple.infra.gathering.command.entity.QGatheringEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetGatheringDao]의 QueryDSL 구현. (조회 전용)
 * 모집중(RECRUITING) 모임을 모임 일시(gathering_at) 오름차순(임박순)으로 read model에 직접 투영한다.
 * imageKey까지만 담고 imageUrl은 서비스가 presign으로 채운다. (soft delete 행은 @SQLRestriction으로 제외)
 * 복합 인덱스 idx_status_type_gathering_at의 선두 status 동등 조건을 활용한다.
 */
@Component
class GetGatheringDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetGatheringDao {

	override fun findRecruitingOrderByGatheringAt(): GatheringViews {
		val gathering: QGatheringEntity = QGatheringEntity.gatheringEntity
		val views: List<GatheringView> = queryFactory
			.select(
				Projections.constructor(
					GatheringView::class.java,
					gathering.id,
					gathering.type,
					gathering.title,
					gathering.imageKey,
					gathering.region,
					gathering.gatheringAt,
				),
			)
			.from(gathering)
			.where(gathering.status.eq(GatheringStatus.RECRUITING))
			.orderBy(gathering.gatheringAt.asc())
			.fetch()
		return GatheringViews(views)
	}
}
