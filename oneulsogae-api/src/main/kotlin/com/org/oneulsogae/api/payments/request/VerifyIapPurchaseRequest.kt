package com.org.oneulsogae.api.payments.request

import com.org.oneulsogae.core.payments.command.application.port.`in`.command.VerifyIapPurchaseCommand
import com.org.oneulsogae.core.payments.command.domain.StorePlatform
import jakarta.validation.constraints.NotBlank

/**
 * 인앱결제 검증 요청 (`POST /coins/v1/iap/purchases`).
 * 앱이 스토어 결제로 받은 영수증(purchaseToken)과 거래 정보를 담는다.
 */
data class VerifyIapPurchaseRequest(
	val platform: StorePlatform,
	@field:NotBlank
	val productId: String,
	/** 통합 영수증 토큰 — iOS JWS / Android purchaseToken. */
	@field:NotBlank
	val purchaseToken: String,
	@field:NotBlank
	val transactionId: String,
) {
	fun toCommand(): VerifyIapPurchaseCommand =
		VerifyIapPurchaseCommand(
			platform = platform,
			productId = productId,
			purchaseToken = purchaseToken,
			transactionId = transactionId,
		)
}
