package com.org.meeple.core.payments.command.application.port.`in`.command

/** 결제완료 접수 명령. 상품은 productId로 지정하고, [paymentKey]는 PG 결제 인증 결과(거래 식별자)다. */
data class CompletePaymentCommand(
	val productId: Long,
	val paymentKey: String,
)
