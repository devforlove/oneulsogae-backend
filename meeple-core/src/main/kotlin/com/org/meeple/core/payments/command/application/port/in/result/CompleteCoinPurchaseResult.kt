package com.org.meeple.core.payments.command.application.port.`in`.result

/**
 * 코인 구매 결제완료 접수 결과.
 * [amount]는 서버가 확정한 실결제가, [coinAmount]는 지급한 코인 개수, [balance]는 지급 후 총 코인 잔액이다.
 */
data class CompleteCoinPurchaseResult(
	val amount: Int,
	val coinAmount: Int,
	val balance: Int,
)
