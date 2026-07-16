package com.org.meeple.infra.gathering.query

import com.org.meeple.admin.gathering.query.dao.GetAdminGatheringMemberDao
import com.org.meeple.admin.gathering.query.dto.AdminGatheringMemberView
import com.org.meeple.admin.gathering.query.dto.AdminGatheringMemberViews
import com.org.meeple.infra.gathering.command.entity.QGatheringMemberEntity
import com.org.meeple.infra.payments.command.entity.QPaymentEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetAdminGatheringMemberDao]의 QueryDSL 구현. (조회 전용)
 * 참가 신청을 user_details(닉네임)·payments(결제금액)와 조인해 read model에 직접 투영한다.
 * 결제금액은 (schedule_id, user_id)의 최신(payment id 최대) 기록 한 건만 조인한다(재접수 시 최신 금액).
 * schedule_id 동등 조건은 ux_schedule_id_user_id 유니크 인덱스의 선두 컬럼으로 커버된다.
 */
@Component
class GetAdminGatheringMemberDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetAdminGatheringMemberDao {

	override fun findByScheduleId(scheduleId: Long): AdminGatheringMemberViews {
		val member: QGatheringMemberEntity = QGatheringMemberEntity.gatheringMemberEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val payment: QPaymentEntity = QPaymentEntity.paymentEntity
		val latestPayment: QPaymentEntity = QPaymentEntity("latestPayment")

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
			.where(member.scheduleId.eq(scheduleId))
			.orderBy(member.id.asc())
			.fetch()
		return AdminGatheringMemberViews(values = views)
	}
}
