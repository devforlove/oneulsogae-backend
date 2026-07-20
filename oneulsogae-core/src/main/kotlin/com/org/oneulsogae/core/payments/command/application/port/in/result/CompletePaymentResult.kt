package com.org.oneulsogae.core.payments.command.application.port.`in`.result

/** 결제완료 접수 결과. [amount]는 접수 시점에 서버가 확정한 실결제가다. */
data class CompletePaymentResult(
	val amount: Int,
)
