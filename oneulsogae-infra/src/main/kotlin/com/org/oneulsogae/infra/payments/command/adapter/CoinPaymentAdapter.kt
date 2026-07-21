package com.org.oneulsogae.infra.payments.command.adapter

import com.org.oneulsogae.core.payments.command.application.port.out.GetCoinPaymentPort
import com.org.oneulsogae.core.payments.command.application.port.out.SaveCoinPaymentPort
import com.org.oneulsogae.core.payments.command.application.port.out.UpdateCoinPaymentStatusPort
import com.org.oneulsogae.core.payments.command.domain.CoinPayment
import com.org.oneulsogae.core.payments.command.domain.PaymentStatus
import com.org.oneulsogae.infra.payments.command.entity.CoinPaymentEntity
import com.org.oneulsogae.infra.payments.command.repository.CoinPaymentJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * [CoinPaymentEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 코인 구매 결제 기록 저장([SaveCoinPaymentPort])·상태 전이([UpdateCoinPaymentStatusPort])·조회([GetCoinPaymentPort]) out-port를 구현한다.
 */
@Component
class CoinPaymentAdapter(
	private val coinPaymentJpaRepository: CoinPaymentJpaRepository,
) : SaveCoinPaymentPort, UpdateCoinPaymentStatusPort, GetCoinPaymentPort {

	override fun save(coinPayment: CoinPayment): CoinPayment {
		val saved: CoinPaymentEntity = coinPaymentJpaRepository.save(
			CoinPaymentEntity(
				userId = coinPayment.userId,
				itemId = coinPayment.itemId,
				coinAmount = coinPayment.coinAmount,
				paymentKey = coinPayment.paymentKey,
				orderId = coinPayment.orderId,
				amount = coinPayment.amount,
				status = coinPayment.status,
			),
		)
		return saved.toDomain()
	}

	@Transactional(readOnly = true)
	override fun findByPaymentKey(paymentKey: String): CoinPayment? =
		coinPaymentJpaRepository.findByPaymentKey(paymentKey)?.toDomain()

	@Transactional
	override fun updateStatus(coinPaymentId: Long, status: PaymentStatus, failReason: String?) {
		val entity: CoinPaymentEntity = coinPaymentJpaRepository.findById(coinPaymentId)
			.orElseThrow { IllegalStateException("코인 결제 기록을 찾을 수 없습니다: $coinPaymentId") }
		entity.status = status
		// 실패 사유는 있을 때만 기록한다(성공 전이는 null → 기존 값 유지). 컬럼 길이 초과분은 잘라 저장 실패를 막는다.
		if (failReason != null) {
			entity.failReason = failReason.take(CoinPaymentEntity.FAIL_REASON_MAX_LENGTH)
		}
	}

	private fun CoinPaymentEntity.toDomain(): CoinPayment =
		CoinPayment(
			id = id,
			userId = userId,
			itemId = itemId,
			coinAmount = coinAmount,
			amount = amount,
			paymentKey = paymentKey,
			orderId = orderId,
			status = status,
		)
}
