package com.org.meeple.infra.gathering.query

import com.org.meeple.admin.gathering.query.dao.GetAdminGatheringDao
import com.org.meeple.admin.gathering.query.dto.AdminGatheringDetailView
import com.org.meeple.admin.gathering.query.dto.AdminGatheringScheduleView
import com.org.meeple.admin.gathering.query.dto.AdminGatheringView
import com.org.meeple.admin.gathering.query.dto.AdminGatheringViews
import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.infra.gathering.command.entity.QGatheringEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringScheduleEntity
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetAdminGatheringDao]의 QueryDSL 구현. (조회 전용)
 * 모임을 저장 날짜(created_at) 내림차순(동률이면 id 내림차순)으로 offset/limit 페이징해 read model에 직접 투영한다.
 * status·type이 주어지면 동등 필터를 적용한다. (soft delete 행은 @SQLRestriction으로 양쪽 쿼리에서 제외)
 * 저장 out-port는 [com.org.meeple.infra.gathering.command.adapter.GatheringAdapter]가 따로 구현한다.
 */
@Component
class GetAdminGatheringDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetAdminGatheringDao {

	override fun findPage(offset: Long, limit: Int, status: GatheringStatus?, type: GatheringType?): AdminGatheringViews {
		val gathering: QGatheringEntity = QGatheringEntity.gatheringEntity
		val views: List<AdminGatheringView> = queryFactory
			.select(
				Projections.constructor(
					AdminGatheringView::class.java,
					gathering.id,
					gathering.type,
					gathering.title,
					gathering.imageKey,
					gathering.region,
					gathering.minParticipants,
					gathering.maxParticipants,
					gathering.status,
					gathering.createdAt,
				),
			)
			.from(gathering)
			.where(statusEq(gathering, status), typeEq(gathering, type))
			.orderBy(gathering.createdAt.desc(), gathering.id.desc())
			.offset(offset)
			.limit(limit.toLong())
			.fetch()
		return AdminGatheringViews(views)
	}

	override fun count(status: GatheringStatus?, type: GatheringType?): Long {
		val gathering: QGatheringEntity = QGatheringEntity.gatheringEntity
		return queryFactory
			.select(gathering.count())
			.from(gathering)
			.where(statusEq(gathering, status), typeEq(gathering, type))
			.fetchOne() ?: 0L
	}

	override fun findDetailById(id: Long): AdminGatheringDetailView? {
		val gathering: QGatheringEntity = QGatheringEntity.gatheringEntity
		return queryFactory
			.select(
				Projections.constructor(
					AdminGatheringDetailView::class.java,
					gathering.id,
					gathering.type,
					gathering.title,
					gathering.description,
					gathering.imageKey,
					gathering.region,
					gathering.minParticipants,
					gathering.maxParticipants,
					gathering.status,
					gathering.createdAt,
				),
			)
			.from(gathering)
			.where(gathering.id.eq(id))
			.fetchOne()
	}

	override fun findSchedulesByGatheringId(gatheringId: Long): List<AdminGatheringScheduleView> {
		val schedule: QGatheringScheduleEntity = QGatheringScheduleEntity.gatheringScheduleEntity
		return queryFactory
			.select(
				Projections.constructor(
					AdminGatheringScheduleView::class.java,
					schedule.id,
					schedule.startAt,
					schedule.endAt,
					schedule.maleFee,
					schedule.femaleFee,
					schedule.earlyBirdMaleFee,
					schedule.earlyBirdFemaleFee,
					schedule.earlyBirdCapacity,
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

	// status가 null이면 null을 반환 → QueryDSL where가 해당 조건을 무시(필터 미적용).
	private fun statusEq(gathering: QGatheringEntity, status: GatheringStatus?): BooleanExpression? =
		status?.let { gathering.status.eq(it) }

	// type이 null이면 null을 반환 → QueryDSL where가 해당 조건을 무시(필터 미적용).
	private fun typeEq(gathering: QGatheringEntity, type: GatheringType?): BooleanExpression? =
		type?.let { gathering.type.eq(it) }
}
