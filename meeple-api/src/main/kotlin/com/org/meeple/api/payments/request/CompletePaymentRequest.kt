package com.org.meeple.api.payments.request

import com.org.meeple.core.payments.command.application.port.`in`.command.CompletePaymentCommand
import jakarta.validation.constraints.NotNull

/** 결제완료 접수 요청. 상품은 productId(모임 상세 응답의 schedules[].productId)로 지정한다. 성별은 받지 않는다(본인 프로필 성별을 서버가 강제). */
data class CompletePaymentRequest(
	@field:NotNull
	val productId: Long?,
) {

	fun toCommand(): CompletePaymentCommand =
		CompletePaymentCommand(productId = productId!!)
}
