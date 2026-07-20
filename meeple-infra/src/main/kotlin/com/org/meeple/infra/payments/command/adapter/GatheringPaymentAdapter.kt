package com.org.meeple.infra.payments.command.adapter

import com.org.meeple.core.payments.command.application.port.out.SaveGatheringPaymentPort
import com.org.meeple.core.payments.command.application.port.out.UpdateGatheringPaymentStatusPort
import com.org.meeple.core.payments.command.domain.GatheringPayment
import com.org.meeple.core.payments.command.domain.PaymentStatus
import com.org.meeple.infra.payments.command.entity.GatheringPaymentEntity
import com.org.meeple.infra.payments.command.repository.GatheringPaymentJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * [GatheringPaymentEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 모임(좌석) 결제 기록 저장([SaveGatheringPaymentPort])·상태 전이([UpdateGatheringPaymentStatusPort]) out-port를 구현한다.
 */
@Component
class GatheringPaymentAdapter(
	private val gatheringPaymentJpaRepository: GatheringPaymentJpaRepository,
) : SaveGatheringPaymentPort, UpdateGatheringPaymentStatusPort {

	override fun save(gatheringPayment: GatheringPayment): GatheringPayment {
		val saved: GatheringPaymentEntity = gatheringPaymentJpaRepository.save(
			GatheringPaymentEntity(
				userId = gatheringPayment.userId,
				gatheringId = gatheringPayment.gatheringId,
				scheduleId = gatheringPayment.scheduleId,
				productId = gatheringPayment.productId,
				gender = gatheringPayment.gender,
				amount = gatheringPayment.amount,
				paymentKey = gatheringPayment.paymentKey,
				orderId = gatheringPayment.orderId,
				status = gatheringPayment.status,
			),
		)
		return GatheringPayment(
			id = saved.id,
			userId = saved.userId,
			gatheringId = saved.gatheringId,
			scheduleId = saved.scheduleId,
			productId = saved.productId,
			gender = saved.gender,
			amount = saved.amount,
			paymentKey = saved.paymentKey,
			orderId = saved.orderId,
			status = saved.status,
		)
	}

	@Transactional
	override fun updateStatus(gatheringPaymentId: Long, status: PaymentStatus, failReason: String?) {
		val entity: GatheringPaymentEntity = gatheringPaymentJpaRepository.findById(gatheringPaymentId)
			.orElseThrow { IllegalStateException("결제 기록을 찾을 수 없습니다: $gatheringPaymentId") }
		entity.status = status
		// 실패 사유는 있을 때만 기록한다(성공 전이는 null → 기존 값 유지). 컬럼 길이 초과분은 잘라 저장 실패를 막는다.
		if (failReason != null) {
			entity.failReason = failReason.take(GatheringPaymentEntity.FAIL_REASON_MAX_LENGTH)
		}
	}
}
