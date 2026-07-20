package com.org.oneulsogae.api.payments.response

import com.org.oneulsogae.core.payments.command.application.port.`in`.result.CompletePaymentResult

/** 결제완료 접수 응답. [amount]는 서버가 확정한 실결제가다. */
data class CompletePaymentResponse(
	val amount: Int,
) {

	companion object {

		fun of(result: CompletePaymentResult): CompletePaymentResponse =
			CompletePaymentResponse(amount = result.amount)
	}
}
