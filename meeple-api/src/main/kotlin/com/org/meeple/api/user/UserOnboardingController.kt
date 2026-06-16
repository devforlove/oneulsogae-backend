package com.org.meeple.api.user

import com.org.meeple.api.user.request.ResolveCompanyNameRequest
import com.org.meeple.api.user.request.UpdateUserDetailRequest
import com.org.meeple.api.user.request.VerifyCompanyEmailRequest
import com.org.meeple.api.user.response.CompanyEmailVerificationResponse
import com.org.meeple.api.user.response.VerifyCompanyEmailResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.user.command.application.port.`in`.RequestCompanyEmailVerificationUseCase
import com.org.meeple.core.user.command.application.port.`in`.ResolveCompanyNameUseCase
import com.org.meeple.core.user.command.application.port.`in`.VerifyCompanyEmailUseCase
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 온보딩 관련 엔드포인트. (모두 인증 필요)
 * - POST /company-email/verifications: 프로필 상세를 저장하고 입력한 회사 이메일로 인증번호를 발송한다.
 * - POST /company-email/verifications/confirm: 사용자가 입력한 인증번호를 검증하고 정식 가입(ACTIVE) 처리한다.
 * - POST /company-name: 회사명을 직접 입력하면 프로필에 반영하고 정식 가입(ACTIVE) 처리한다.
 */
@RestController
@RequestMapping("/users/v1/onboarding")
class UserOnboardingController(
	private val requestCompanyEmailVerificationUseCase: RequestCompanyEmailVerificationUseCase,
	private val verifyCompanyEmailUseCase: VerifyCompanyEmailUseCase,
	private val resolveCompanyNameUseCase: ResolveCompanyNameUseCase,
) {

	/** 온보딩 입력값(프로필 상세)을 저장하고, 입력한 회사 이메일로 인증번호를 발송한다. */
	@PostMapping("/company-email/verifications")
	fun requestCompanyEmailVerification(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: UpdateUserDetailRequest,
	): ApiResponse<CompanyEmailVerificationResponse> =
		ApiResponse.success(
			CompanyEmailVerificationResponse.of(
				requestCompanyEmailVerificationUseCase.request(user.id, request.toCommand()),
			),
		)

	/**
	 * 사용자가 입력한 인증번호를 검증한다. 성공하면 회사 이메일/회사명을 확정하고 정식 가입(ACTIVE) 처리한다.
	 * 회사명 매핑 성공 여부(isCompanyResolved)를 응답으로 내려준다.
	 */
	@PostMapping("/company-email/verifications/confirm")
	fun confirmCompanyEmailVerification(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: VerifyCompanyEmailRequest,
	): ApiResponse<VerifyCompanyEmailResponse> =
		ApiResponse.success(VerifyCompanyEmailResponse.of(verifyCompanyEmailUseCase.verify(user.id, request.code)))

	/** 사용자가 회사명을 직접 입력하면 프로필에 반영하고 정식 가입(ACTIVE) 처리한다. */
	@PostMapping("/company-name")
	fun resolveCompanyName(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: ResolveCompanyNameRequest,
	): ApiResponse<Unit> {
		resolveCompanyNameUseCase.resolve(user.id, request.companyName)
		return ApiResponse.success()
	}
}
