package com.org.meeple.core.payments.command.application.port.`in`.command

/** 코인 구매 결제완료 접수 명령. 상품은 itemId로, PG 결제 인증 결과는 paymentKey·orderId로 지정한다. */
data class CompleteCoinPurchaseCommand(
	val itemId: Long,
	val paymentKey: String,
	val orderId: String,
)
