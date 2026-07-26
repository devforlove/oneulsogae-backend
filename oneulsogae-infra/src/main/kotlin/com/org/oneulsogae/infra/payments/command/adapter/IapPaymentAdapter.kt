package com.org.oneulsogae.infra.payments.command.adapter

import com.org.oneulsogae.core.payments.command.application.port.out.GetIapPaymentPort
import com.org.oneulsogae.core.payments.command.application.port.out.SaveIapPaymentPort
import com.org.oneulsogae.core.payments.command.domain.IapPayment
import com.org.oneulsogae.infra.payments.command.entity.IapPaymentEntity
import com.org.oneulsogae.infra.payments.command.repository.IapPaymentJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * [IapPaymentEntity] command 영속성 어댑터. 저장([SaveIapPaymentPort])·조회([GetIapPaymentPort]) out-port를 구현한다.
 * transaction_id 유니크 위반은 saveAndFlush 시점에 DataIntegrityViolationException으로 표면화한다(호출 서비스가 멱등 처리).
 */
@Component
class IapPaymentAdapter(
	private val iapPaymentJpaRepository: IapPaymentJpaRepository,
) : SaveIapPaymentPort, GetIapPaymentPort {

	override fun save(iapPayment: IapPayment): IapPayment =
		iapPaymentJpaRepository.saveAndFlush(
			IapPaymentEntity(
				userId = iapPayment.userId,
				itemId = iapPayment.itemId,
				platform = iapPayment.platform,
				productId = iapPayment.productId,
				transactionId = iapPayment.transactionId,
				coinAmount = iapPayment.coinAmount,
				status = iapPayment.status,
			),
		).toDomain()

	@Transactional(readOnly = true)
	override fun findByTransactionId(transactionId: String): IapPayment? =
		iapPaymentJpaRepository.findByTransactionId(transactionId)?.toDomain()

	private fun IapPaymentEntity.toDomain(): IapPayment =
		IapPayment(
			id = id,
			userId = userId,
			itemId = itemId,
			platform = platform,
			productId = productId,
			transactionId = transactionId,
			coinAmount = coinAmount,
			status = status,
		)
}
