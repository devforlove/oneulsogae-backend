package com.org.oneulsogae.api.payments.response

import com.org.oneulsogae.core.payments.command.application.port.`in`.result.CompleteCoinPurchaseResult

/**
 * 코인 구매 결제완료 접수 응답.
 * [amount]는 서버가 확정한 실결제가, [coinAmount]는 지급한 코인 개수, [balance]는 지급 후 총 코인 잔액이다.
 */
data class CompleteCoinPurchaseResponse(
	val amount: Int,
	val coinAmount: Int,
	val balance: Int,
) {

	companion object {

		fun of(result: CompleteCoinPurchaseResult): CompleteCoinPurchaseResponse =
			CompleteCoinPurchaseResponse(
				amount = result.amount,
				coinAmount = result.coinAmount,
				balance = result.balance,
			)
	}
}
