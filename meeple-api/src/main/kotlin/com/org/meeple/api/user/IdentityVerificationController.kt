package com.org.meeple.api.user

import com.org.meeple.api.user.request.ConfirmIdentityVerificationRequest
import com.org.meeple.api.user.response.ConfirmIdentityVerificationResponse
import com.org.meeple.api.user.response.RegisterIdentityVerificationResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.user.command.application.port.`in`.ConfirmIdentityVerificationUseCase
import com.org.meeple.core.user.command.application.port.`in`.RegisterIdentityVerificationUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users/v1/identity-verification")
@Tag(name = "본인확인(KCP)", description = "온보딩 본인확인 엔드포인트 (모두 인증 필요)")
class IdentityVerificationController(
	private val registerIdentityVerificationUseCase: RegisterIdentityVerificationUseCase,
	private val confirmIdentityVerificationUseCase: ConfirmIdentityVerificationUseCase,
) {

	@Operation(summary = "본인확인 거래등록", description = "KCP 인증창 호출용 callUrl·regCertKey·ordrIdxx를 반환한다.")
	@PostMapping("/register")
	fun register(
		@LoginUser user: AuthUser,
	): ApiResponse<RegisterIdentityVerificationResponse> =
		ApiResponse.success(
			RegisterIdentityVerificationResponse.of(registerIdentityVerificationUseCase.register(user.id)),
		)

	@Operation(summary = "본인확인 결과 확정", description = "인증창 결과의 regCertKey·ordrIdxx로 결과를 조회·검증·저장한다.")
	@PostMapping("/confirm")
	fun confirm(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: ConfirmIdentityVerificationRequest,
	): ApiResponse<ConfirmIdentityVerificationResponse> =
		ApiResponse.success(
			ConfirmIdentityVerificationResponse.of(
				confirmIdentityVerificationUseCase.confirm(user.id, request.toCommand()),
			),
		)
}
