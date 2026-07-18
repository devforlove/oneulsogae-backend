package com.org.meeple.infra.payments.command.adapter

import com.org.meeple.core.payments.command.application.port.out.SavePaymentPort
import com.org.meeple.core.payments.command.application.port.out.UpdatePaymentStatusPort
import com.org.meeple.core.payments.command.domain.Payment
import com.org.meeple.core.payments.command.domain.PaymentStatus
import com.org.meeple.infra.payments.command.entity.PaymentEntity
import com.org.meeple.infra.payments.command.repository.PaymentJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * [PaymentEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 결제 기록 저장([SavePaymentPort])·상태 전이([UpdatePaymentStatusPort]) out-port를 구현한다.
 */
@Component
class PaymentAdapter(
	private val paymentJpaRepository: PaymentJpaRepository,
) : SavePaymentPort, UpdatePaymentStatusPort {

	override fun save(payment: Payment): Payment {
		val saved: PaymentEntity = paymentJpaRepository.save(
			PaymentEntity(
				userId = payment.userId,
				gatheringId = payment.gatheringId,
				scheduleId = payment.scheduleId,
				productId = payment.productId,
				gender = payment.gender,
				amount = payment.amount,
				paymentKey = payment.paymentKey,
				orderId = payment.orderId,
				status = payment.status,
			),
		)
		return Payment(
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
	override fun updateStatus(paymentId: Long, status: PaymentStatus, failReason: String?) {
		val entity: PaymentEntity = paymentJpaRepository.findById(paymentId)
			.orElseThrow { IllegalStateException("결제 기록을 찾을 수 없습니다: $paymentId") }
		entity.status = status
		// 실패 사유는 있을 때만 기록한다(성공 전이는 null → 기존 값 유지). 컬럼 길이 초과분은 잘라 저장 실패를 막는다.
		if (failReason != null) {
			entity.failReason = failReason.take(PaymentEntity.FAIL_REASON_MAX_LENGTH)
		}
	}
}
