package com.org.meeple.api.payments.request

import com.org.meeple.core.payments.command.application.port.`in`.command.CompletePaymentCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/** 결제완료 접수 요청. 상품은 productId로, PG 결제 인증 결과는 paymentKey·orderId로 지정한다. */
data class CompletePaymentRequest(
	@field:NotNull
	val productId: Long?,

	@field:NotBlank
	val paymentKey: String?,

	@field:NotBlank
	val orderId: String?,
) {

	fun toCommand(): CompletePaymentCommand =
		CompletePaymentCommand(productId = productId!!, paymentKey = paymentKey!!, orderId = orderId!!)
}
