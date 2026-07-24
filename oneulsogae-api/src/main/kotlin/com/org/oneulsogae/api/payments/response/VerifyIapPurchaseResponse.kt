package com.org.oneulsogae.api.payments.response

import com.org.oneulsogae.core.payments.command.application.port.`in`.result.VerifyIapPurchaseResult

/** 인앱결제 검증·적립 응답 — 적립 후 총 코인 잔액. */
data class VerifyIapPurchaseResponse(
	val coinBalance: Int,
) {
	companion object {
		fun of(result: VerifyIapPurchaseResult): VerifyIapPurchaseResponse =
			VerifyIapPurchaseResponse(coinBalance = result.coinBalance)
	}
}
