package com.org.meeple.api.payments.request

import com.org.meeple.core.payments.command.application.port.`in`.command.CompleteCoinPurchaseCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/** 코인 구매 결제완료 접수 요청. 상품은 itemId로, PG 결제 인증 결과는 paymentKey·orderId로 지정한다. */
data class CompleteCoinPurchaseRequest(
	@field:NotNull
	val itemId: Long?,

	@field:NotBlank
	val paymentKey: String?,

	@field:NotBlank
	val orderId: String?,
) {

	fun toCommand(): CompleteCoinPurchaseCommand =
		CompleteCoinPurchaseCommand(itemId = itemId!!, paymentKey = paymentKey!!, orderId = orderId!!)
}
