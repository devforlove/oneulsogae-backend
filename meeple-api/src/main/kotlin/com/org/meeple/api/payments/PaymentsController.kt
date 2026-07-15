package com.org.meeple.api.payments

import com.org.meeple.api.payments.response.CheckoutResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.payments.query.service.port.`in`.GetCheckoutUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "결제", description = "결제(체크아웃) 화면 데이터 조회")
@RestController
@RequestMapping("/payments/v1")
class PaymentsController(
	private val getCheckoutUseCase: GetCheckoutUseCase,
) {

	/** 결제 화면 진입 시 필요한 주문자 정보를 조회한다. (모임·일정·금액은 offline API가 제공) */
	@Operation(
		summary = "체크아웃 화면 조회",
		description = "결제 화면 진입 시 필요한 주문자 정보(실명·이메일·휴대폰)를 조회한다. 본인인증 전 사용자는 각 필드가 null일 수 있다.",
	)
	@GetMapping("/checkout")
	fun getCheckout(
		@LoginUser user: AuthUser,
	): ApiResponse<CheckoutResponse> =
		ApiResponse.success(CheckoutResponse.of(getCheckoutUseCase.getCheckout(user.id)))
}
