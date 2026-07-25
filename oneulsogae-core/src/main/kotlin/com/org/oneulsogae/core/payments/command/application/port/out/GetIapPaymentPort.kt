package com.org.oneulsogae.core.payments.command.application.port.out

import com.org.oneulsogae.core.payments.command.domain.IapPayment

/** 인앱결제 기록 조회 out-port. 같은 transaction_id 재검증을 멱등 처리하는 데 쓴다. */
interface GetIapPaymentPort {

	fun findByTransactionId(transactionId: String): IapPayment?
}
