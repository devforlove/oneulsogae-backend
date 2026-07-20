package com.org.oneulsogae.api.user

import com.org.oneulsogae.api.user.request.RequestUniversityEmailRequest
import com.org.oneulsogae.api.user.request.VerifyUniversityEmailRequest
import com.org.oneulsogae.api.user.response.UniversityEmailVerificationResponse
import com.org.oneulsogae.api.user.response.VerifyUniversityEmailResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.user.command.application.port.`in`.RequestUniversityEmailVerificationUseCase
import com.org.oneulsogae.core.user.command.application.port.`in`.VerifyUniversityEmailUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 학교 인증(선택적 추가 인증) 엔드포인트. (모두 인증 필요, 가입 상태와 무관)
 * - POST /university-email/verifications: 입력한 학교 이메일로 인증번호를 발송한다.
 * - POST /university-email/verifications/confirm: 인증번호를 검증하고 학교 이메일/학교명을 프로필·매칭 읽기 모델에 기록한다.
 */
@RestController
@RequestMapping("/users/v1")
@Tag(name = "유저 학교 인증", description = "학교 인증(선택적 추가 인증) 엔드포인트 (모두 인증 필요)")
class UserUniversityVerificationController(
	private val requestUniversityEmailVerificationUseCase: RequestUniversityEmailVerificationUseCase,
	private val verifyUniversityEmailUseCase: VerifyUniversityEmailUseCase,
) {

	/** 입력한 학교 이메일로 인증번호를 발송한다. */
	@Operation(summary = "학교 이메일 인증번호 발송", description = "입력한 학교 이메일로 인증번호를 발송한다.")
	@PostMapping("/university-email/verifications")
	fun requestUniversityEmailVerification(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: RequestUniversityEmailRequest,
	): ApiResponse<UniversityEmailVerificationResponse> =
		ApiResponse.success(
			UniversityEmailVerificationResponse.of(
				requestUniversityEmailVerificationUseCase.request(user.id, request.universityEmail),
			),
		)

	/**
	 * 사용자가 입력한 인증번호를 검증한다. 성공하면 학교 이메일/학교명을 프로필·매칭 읽기 모델에 기록한다.
	 * 학교명 매핑 성공 여부(isUniversityResolved)를 응답으로 내려준다.
	 */
	@Operation(summary = "학교 이메일 인증번호 확인", description = "사용자가 입력한 인증번호를 검증하고, 성공하면 학교 이메일/학교명을 프로필·매칭 읽기 모델에 기록한다.")
	@PostMapping("/university-email/verifications/confirm")
	fun confirmUniversityEmailVerification(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: VerifyUniversityEmailRequest,
	): ApiResponse<VerifyUniversityEmailResponse> =
		ApiResponse.success(VerifyUniversityEmailResponse.of(verifyUniversityEmailUseCase.verify(user.id, request.code)))
}
