package com.org.meeple.api.user

import com.org.meeple.api.user.request.RequestCompanyEmailVerificationRequest
import com.org.meeple.api.user.request.ResolveCompanyNameRequest
import com.org.meeple.api.user.request.VerifyCompanyEmailRequest
import com.org.meeple.api.user.response.CompanyEmailVerificationResponse
import com.org.meeple.api.user.response.VerifyCompanyEmailResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.user.command.application.port.`in`.RequestCompanyEmailVerificationUseCase
import com.org.meeple.core.user.command.application.port.`in`.ResolveCompanyNameUseCase
import com.org.meeple.core.user.command.application.port.`in`.VerifyCompanyEmailUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 회사 이메일 인증(직장 인증) 엔드포인트. (모두 인증 필요, 온보딩과 분리된 플로우)
 * - POST /company-email/verifications: 입력한 회사 이메일로 인증번호를 발송한다.
 * - POST /company-email/verifications/confirm: 입력한 인증번호를 검증하고 회사 이메일/회사명을 프로필에 확정한다.
 * - POST /company-name: 회사명을 직접 입력하면 프로필에 반영한다.
 */
@RestController
@RequestMapping("/users/v1/onboarding")
@Tag(name = "회사 이메일 인증", description = "회사 이메일 인증(직장 인증) 엔드포인트 (모두 인증 필요)")
class UserCompanyEmailVerificationController(
	private val requestCompanyEmailVerificationUseCase: RequestCompanyEmailVerificationUseCase,
	private val verifyCompanyEmailUseCase: VerifyCompanyEmailUseCase,
	private val resolveCompanyNameUseCase: ResolveCompanyNameUseCase,
) {

	/** 입력한 회사 이메일로 인증번호를 발송한다. */
	@Operation(summary = "회사 이메일 인증번호 발송", description = "입력한 회사 이메일로 인증번호를 발송한다.")
	@PostMapping("/company-email/verifications")
	fun requestCompanyEmailVerification(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: RequestCompanyEmailVerificationRequest,
	): ApiResponse<CompanyEmailVerificationResponse> =
		ApiResponse.success(
			CompanyEmailVerificationResponse.of(
				requestCompanyEmailVerificationUseCase.request(user.id, request.companyEmail),
			),
		)

	/**
	 * 사용자가 입력한 인증번호를 검증한다. 성공하면 회사 이메일/회사명을 프로필에 확정한다.
	 * 회사명 매핑 성공 여부(isCompanyResolved)를 응답으로 내려준다.
	 */
	@Operation(summary = "회사 이메일 인증번호 확인", description = "사용자가 입력한 인증번호를 검증하고, 성공하면 회사 이메일/회사명을 프로필에 확정한다.")
	@PostMapping("/company-email/verifications/confirm")
	fun confirmCompanyEmailVerification(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: VerifyCompanyEmailRequest,
	): ApiResponse<VerifyCompanyEmailResponse> =
		ApiResponse.success(VerifyCompanyEmailResponse.of(verifyCompanyEmailUseCase.verify(user.id, request.code)))

	/** 사용자가 회사명을 직접 입력하면 프로필에 반영한다. */
	@Operation(summary = "회사명 직접 입력", description = "사용자가 회사명을 직접 입력하면 프로필에 반영한다.")
	@PostMapping("/company-name")
	fun resolveCompanyName(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: ResolveCompanyNameRequest,
	): ApiResponse<Unit> {
		resolveCompanyNameUseCase.resolve(user.id, request.companyName)
		return ApiResponse.success()
	}
}
