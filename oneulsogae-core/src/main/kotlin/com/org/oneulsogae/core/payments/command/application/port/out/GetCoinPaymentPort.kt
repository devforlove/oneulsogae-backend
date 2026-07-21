package com.org.oneulsogae.core.payments.command.application.port.out

import com.org.oneulsogae.core.payments.command.domain.CoinPayment

/** 코인 구매 결제 기록 조회 아웃포트. 같은 paymentKey의 재접수를 식별해 멱등 처리하는 데 쓴다. */
interface GetCoinPaymentPort {

	fun findByPaymentKey(paymentKey: String): CoinPayment?
}
