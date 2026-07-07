package com.org.meeple.infra.gathering.query

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.core.gathering.query.dao.GetGatheringDao
import com.org.meeple.core.gathering.query.dto.GatheringDetailView
import com.org.meeple.core.gathering.query.dto.GatheringScheduleView
import com.org.meeple.core.gathering.query.dto.GatheringView
import com.org.meeple.core.gathering.query.dto.GatheringViews
import com.org.meeple.infra.gathering.command.entity.QGatheringEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringScheduleEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetGatheringDao]의 QueryDSL 구현. (조회 전용)
 * 모집중(RECRUITING) 모임을 최신 등록순(created_at 내림차순, 동률이면 id 내림차순)으로 read model에 직접 투영한다.
 * imageKey까지만 담고 imageUrl은 서비스가 presign으로 채운다. (soft delete 행은 @SQLRestriction으로 제외)
 * 복합 인덱스 idx_status_type의 선두 status 동등 조건은 seek로 받쳐진다.
 * 상세 조회는 id + status=RECRUITING 동등 조건으로 단건 투영한다(모집중이 아니면 null → 서비스가 404).
 */
@Component
class GetGatheringDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetGatheringDao {

	override fun findRecruiting(): GatheringViews {
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
				),
			)
			.from(gathering)
			.where(gathering.status.eq(GatheringStatus.RECRUITING))
			.orderBy(gathering.createdAt.desc(), gathering.id.desc())
			.fetch()
		return GatheringViews(views)
	}

	override fun findRecruitingDetailById(id: Long): GatheringDetailView? {
		val gathering: QGatheringEntity = QGatheringEntity.gatheringEntity
		return queryFactory
			.select(
				Projections.constructor(
					GatheringDetailView::class.java,
					gathering.id,
					gathering.type,
					gathering.title,
					gathering.description,
					gathering.imageKey,
					gathering.region,
					gathering.minParticipants,
					gathering.maxParticipants,
				),
			)
			.from(gathering)
			.where(gathering.id.eq(id), gathering.status.eq(GatheringStatus.RECRUITING))
			.fetchOne()
	}

	override fun findSchedulesByGatheringId(gatheringId: Long): List<GatheringScheduleView> {
		val schedule: QGatheringScheduleEntity = QGatheringScheduleEntity.gatheringScheduleEntity
		return queryFactory
			.select(
				Projections.constructor(
					GatheringScheduleView::class.java,
					schedule.id,
					schedule.startAt,
					schedule.endAt,
					schedule.maleFee,
					schedule.femaleFee,
					schedule.maleRemaining,
					schedule.femaleRemaining,
					schedule.earlyBirdDiscountRate,
					schedule.earlyBirdCapacity,
					schedule.earlyBirdRemaining,
					schedule.discountMaleFee,
					schedule.discountFemaleFee,
					schedule.status,
				),
			)
			.from(schedule)
			.where(schedule.gatheringId.eq(gatheringId))
			.orderBy(schedule.startAt.asc())
			.fetch()
	}
}
