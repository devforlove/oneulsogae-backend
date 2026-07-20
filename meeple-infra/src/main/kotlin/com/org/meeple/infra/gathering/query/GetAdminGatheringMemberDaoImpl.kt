package com.org.meeple.infra.gathering.query

import com.org.meeple.admin.gathering.query.dao.GetAdminGatheringMemberDao
import com.org.meeple.admin.gathering.query.dto.AdminGatheringMemberDetailView
import com.org.meeple.admin.gathering.query.dto.AdminGatheringMemberView
import com.org.meeple.admin.gathering.query.dto.AdminGatheringMemberViews
import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.infra.gathering.command.entity.QGatheringEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringMemberEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringProfileEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringScheduleEntity
import com.org.meeple.infra.gathering.command.entity.QMemberVerificationEntity
import com.org.meeple.infra.payments.command.entity.QGatheringPaymentEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetAdminGatheringMemberDao]의 QueryDSL 구현. (조회 전용)
 * 목록: 참가 신청을 user_details(닉네임)·gathering_payments(결제금액)와 조인해 read model에 직접 투영한다.
 * 결제금액은 (schedule_id, user_id)의 최신(payment id 최대) 기록 한 건만 조인한다(재접수 시 최신 금액).
 * schedule_id 동등 조건은 ux_schedule_id_user_id 유니크 인덱스의 선두 컬럼으로 커버된다. (status 필터는 있으면 동등 조건)
 * 상세: 신청 유저의 gathering_profile을 left join해 모임 프로필을 투영한다.
 */
@Component
class GetAdminGatheringMemberDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetAdminGatheringMemberDao {

	override fun findPage(
		scheduleId: Long?,
		offset: Long,
		limit: Int,
		status: GatheringMemberStatus?,
	): AdminGatheringMemberViews {
		val member: QGatheringMemberEntity = QGatheringMemberEntity.gatheringMemberEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val payment: QGatheringPaymentEntity = QGatheringPaymentEntity.gatheringPaymentEntity
		val latestPayment: QGatheringPaymentEntity = QGatheringPaymentEntity("latestPayment")
		val gathering: QGatheringEntity = QGatheringEntity.gatheringEntity
		val schedule: QGatheringScheduleEntity = QGatheringScheduleEntity.gatheringScheduleEntity
		val profile: QGatheringProfileEntity = QGatheringProfileEntity.gatheringProfileEntity
		val verification: QMemberVerificationEntity = QMemberVerificationEntity.memberVerificationEntity
		val latestVerification: QMemberVerificationEntity = QMemberVerificationEntity("latestVerification")

		val views: List<AdminGatheringMemberView> = queryFactory
			.select(
				Projections.constructor(
					AdminGatheringMemberView::class.java,
					member.id,
					member.userId,
					detail.nickname,
					member.gender,
					member.status,
					payment.amount,
					member.createdAt,
					member.scheduleId,
					gathering.title,
					schedule.startAt,
					profile.id.isNotNull(),
					verification.id,
				),
			)
			.from(member)
			.leftJoin(detail).on(detail.userId.eq(member.userId))
			.leftJoin(payment).on(
				payment.id.eq(
					JPAExpressions.select(latestPayment.id.max())
						.from(latestPayment)
						.where(
							latestPayment.scheduleId.eq(member.scheduleId),
							latestPayment.userId.eq(member.userId),
						),
				),
			)
			.leftJoin(gathering).on(gathering.id.eq(member.gatheringId))
			.leftJoin(schedule).on(schedule.id.eq(member.scheduleId))
			.leftJoin(profile).on(profile.userId.eq(member.userId))
			.leftJoin(verification).on(
				verification.id.eq(
					JPAExpressions.select(latestVerification.id.max())
						.from(latestVerification)
						.where(latestVerification.userId.eq(member.userId)),
				),
			)
			.where(scheduleIdEq(member, scheduleId), statusEq(member, status))
			.orderBy(member.id.asc())
			.offset(offset)
			.limit(limit.toLong())
			.fetch()
		return AdminGatheringMemberViews(values = views)
	}

	override fun count(scheduleId: Long?, status: GatheringMemberStatus?): Long {
		val member: QGatheringMemberEntity = QGatheringMemberEntity.gatheringMemberEntity
		return queryFactory
			.select(member.count())
			.from(member)
			.where(scheduleIdEq(member, scheduleId), statusEq(member, status))
			.fetchOne() ?: 0L
	}

	override fun findMemberProfile(scheduleId: Long, memberId: Long): AdminGatheringMemberDetailView? {
		val member: QGatheringMemberEntity = QGatheringMemberEntity.gatheringMemberEntity
		val profile: QGatheringProfileEntity = QGatheringProfileEntity.gatheringProfileEntity

		return queryFactory
			.select(
				Projections.constructor(
					AdminGatheringMemberDetailView::class.java,
					profile.jobCategory,
					profile.jobDetail,
					profile.birthday,
					profile.height,
					profile.profileImageCode,
				),
			)
			.from(member)
			.leftJoin(profile).on(profile.userId.eq(member.userId))
			.where(member.id.eq(memberId), member.scheduleId.eq(scheduleId))
			.fetchOne()
	}

	/** scheduleId가 있으면 동등 조건(그 일정만), 없으면 null(=where 무시, 전역 조회). */
	private fun scheduleIdEq(
		member: QGatheringMemberEntity,
		scheduleId: Long?,
	): BooleanExpression? =
		scheduleId?.let { member.scheduleId.eq(it) }

	/** status가 있으면 동등 조건, 없으면 null(=where 무시). */
	private fun statusEq(
		member: QGatheringMemberEntity,
		status: GatheringMemberStatus?,
	): BooleanExpression? =
		status?.let { member.status.eq(it) }
}
