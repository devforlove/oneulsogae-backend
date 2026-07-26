package com.org.oneulsogae.infra.payments.command.repository

import com.org.oneulsogae.infra.payments.command.entity.IapPaymentEntity
import org.springframework.data.jpa.repository.JpaRepository

/** 인앱결제 기록 리포지토리. 도메인 포트는 어댑터가 구현한다. */
interface IapPaymentJpaRepository : JpaRepository<IapPaymentEntity, Long> {

	fun findByTransactionId(transactionId: String): IapPaymentEntity?
}
