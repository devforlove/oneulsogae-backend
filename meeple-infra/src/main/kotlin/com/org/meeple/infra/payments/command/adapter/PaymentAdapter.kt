package com.org.meeple.infra.payments.command.adapter

import com.org.meeple.core.payments.command.application.port.out.SavePaymentPort
import com.org.meeple.core.payments.command.domain.Payment
import com.org.meeple.infra.payments.command.entity.PaymentEntity
import com.org.meeple.infra.payments.command.repository.PaymentJpaRepository
import org.springframework.stereotype.Component

/**
 * [PaymentEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 결제 기록 저장([SavePaymentPort]) out-port를 구현한다.
 */
@Component
class PaymentAdapter(
	private val paymentJpaRepository: PaymentJpaRepository,
) : SavePaymentPort {

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
		)
	}
}
