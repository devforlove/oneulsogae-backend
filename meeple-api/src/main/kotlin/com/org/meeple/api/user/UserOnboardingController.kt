package com.org.meeple.api.user

import com.org.meeple.api.user.request.UpdateUserDetailRequest
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.user.command.application.port.`in`.CompleteOnboardingUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 온보딩 관련 엔드포인트. (모두 인증 필요)
 * - POST /complete: 프로필 상세를 저장하고 정식 가입(ACTIVE) 처리한다.
 */
@RestController
@RequestMapping("/users/v1/onboarding")
@Tag(name = "유저 온보딩", description = "온보딩 관련 엔드포인트 (모두 인증 필요)")
class UserOnboardingController(
	private val completeOnboardingUseCase: CompleteOnboardingUseCase,
) {

	/** 온보딩 입력값(프로필 상세)을 저장하고 정식 가입(ACTIVE) 처리한다. */
	@Operation(summary = "온보딩 완료", description = "온보딩 입력값(프로필 상세)을 저장하고 정식 가입(ACTIVE) 처리한다.")
	@PostMapping("/complete")
	fun complete(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: UpdateUserDetailRequest,
	): ApiResponse<Unit> {
		completeOnboardingUseCase.complete(user.id, request.toCommand())
		return ApiResponse.success()
	}
}
