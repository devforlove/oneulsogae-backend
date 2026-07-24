package com.org.oneulsogae.api.payments

import com.org.oneulsogae.api.payments.request.VerifyIapPurchaseRequest
import com.org.oneulsogae.api.payments.response.VerifyIapPurchaseResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.payments.command.application.port.`in`.VerifyIapPurchaseUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.validation.Valid

/**
 * 인앱결제(App Store / Google Play) 코인 구매 엔드포인트. (인증 필요)
 * 앱이 스토어 결제로 받은 영수증을 검증해 코인을 적립한다. (PG 결제 /coin/complete와 별개)
 */
@Tag(name = "인앱결제", description = "App Store / Google Play 인앱결제 영수증 검증·코인 적립")
@RestController
@RequestMapping("/coins/v1/iap")
class IapPurchaseController(
	private val verifyIapPurchaseUseCase: VerifyIapPurchaseUseCase,
) {

	/**
	 * 스토어 영수증을 검증하고 코인을 적립한다. 성공 시 적립 후 코인 잔액을 반환한다.
	 * (영수증 위조·환불·중복 소비는 스토어 검증에서 걸러진다)
	 */
	@Operation(
		summary = "인앱결제 검증·적립",
		description = "앱이 스토어 결제로 받은 영수증(purchaseToken)을 Apple/Google에 검증하고, 성공 시 코인을 적립해 잔액을 반환한다.",
	)
	@PostMapping("/purchases")
	fun verifyPurchase(
		@LoginUser user: AuthUser,
		@RequestBody @Valid request: VerifyIapPurchaseRequest,
	): ApiResponse<VerifyIapPurchaseResponse> =
		ApiResponse.success(VerifyIapPurchaseResponse.of(verifyIapPurchaseUseCase.verify(user.id, request.toCommand())))
}
