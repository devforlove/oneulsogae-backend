package com.org.oneulsogae.infra.payments.command.repository

import com.org.oneulsogae.infra.payments.command.entity.CoinPaymentEntity
import org.springframework.data.jpa.repository.JpaRepository

interface CoinPaymentJpaRepository : JpaRepository<CoinPaymentEntity, Long> {

	/** paymentKey는 유니크(uk_payment_key)라 단건이다. 인덱스 seek. */
	fun findByPaymentKey(paymentKey: String): CoinPaymentEntity?
}
