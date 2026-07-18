package com.org.meeple.infra.gathering.query

import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.core.gathering.query.dao.GetGatheringDao
import com.org.meeple.core.gathering.query.dto.GatheringDetailView
import com.org.meeple.core.gathering.query.dto.GatheringParticipantView
import com.org.meeple.core.gathering.query.dto.GatheringProductIdentity
import com.org.meeple.core.gathering.query.dto.GatheringProductView
import com.org.meeple.core.gathering.query.dto.GatheringScheduleView
import com.org.meeple.core.gathering.query.dto.GatheringView
import com.org.meeple.core.gathering.query.dto.GatheringViews
import com.org.meeple.infra.gathering.command.entity.GatheringProductEntity
import com.org.meeple.infra.gathering.command.entity.GatheringScheduleEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringMemberEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringProductEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringScheduleEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
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
		val rows: List<GatheringScheduleEntity> = queryFactory
			.selectFrom(schedule)
			.where(schedule.gatheringId.eq(gatheringId))
			.orderBy(schedule.startAt.asc())
			.fetch()
		if (rows.isEmpty()) return emptyList()

		val product: QGatheringProductEntity = QGatheringProductEntity.gatheringProductEntity
		val productsBySchedule: Map<Long, List<GatheringProductView>> = queryFactory
			.selectFrom(product)
			.where(product.scheduleId.`in`(rows.map { row: GatheringScheduleEntity -> checkNotNull(row.id) }))
			.fetch()
			.groupBy(
				{ row: GatheringProductEntity -> row.scheduleId },
				{ row: GatheringProductEntity -> GatheringProductView(id = checkNotNull(row.id), gender = row.gender, type = row.type, price = row.price) },
			)

		return rows.map { row: GatheringScheduleEntity ->
			GatheringScheduleView(
				id = checkNotNull(row.id),
				startAt = row.startAt,
				endAt = row.endAt,
				maleRemaining = row.maleRemaining,
				femaleRemaining = row.femaleRemaining,
				earlyBirdCapacity = row.earlyBirdCapacity,
				earlyBirdRemaining = row.earlyBirdRemaining,
				status = row.status,
				products = productsBySchedule[row.id] ?: emptyList(),
			)
		}
	}

	override fun findParticipantsByScheduleIds(scheduleIds: List<Long>): List<GatheringParticipantView> {
		if (scheduleIds.isEmpty()) return emptyList()

		val member: QGatheringMemberEntity = QGatheringMemberEntity.gatheringMemberEntity
		val userDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		// WHERE의 schedule_id IN은 유니크 인덱스 ux_schedule_id_user_id의 선두 컬럼이라 일정별 seek를 탄다.
		// 프로필 누락(user_details 없음)에도 참가자 행은 남겨야 하므로 left join으로 조인한다.
		return queryFactory
			.select(
				Projections.constructor(
					GatheringParticipantView::class.java,
					member.scheduleId,
					member.userId,
					member.status,
					member.gender,
					userDetail.nickname,
					userDetail.profileImageCode,
					userDetail.birthday,
				),
			)
			.from(member)
			.leftJoin(userDetail).on(userDetail.userId.eq(member.userId))
			.where(
				member.scheduleId.`in`(scheduleIds),
				member.status.`in`(GatheringMemberStatus.PENDING, GatheringMemberStatus.JOINED),
			)
			.orderBy(member.scheduleId.asc(), member.id.asc())
			.fetch()
	}

	override fun findProductById(productId: Long): GatheringProductIdentity? {
		val product: QGatheringProductEntity = QGatheringProductEntity.gatheringProductEntity
		return queryFactory
			.select(
				Projections.constructor(
					GatheringProductIdentity::class.java,
					product.id,
					product.gatheringId,
					product.scheduleId,
					product.gender,
					product.type,
				),
			)
			.from(product)
			.where(product.id.eq(productId))
			.fetchOne()
	}
}
