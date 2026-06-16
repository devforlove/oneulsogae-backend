package com.org.meeple.api.user

import com.org.meeple.api.match.request.UpdateProfileRequest
import com.org.meeple.api.match.response.ProfileOptionsResponse
import com.org.meeple.api.user.response.UserProfileResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.user.query.service.port.`in`.GetUserDetailUseCase
import com.org.meeple.core.user.command.service.port.`in`.UpdateProfileUseCase
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users/v1/profile")
class UserProfileController(
	private val getUserDetailUseCase: GetUserDetailUseCase,
	private val updateProfileUseCase: UpdateProfileUseCase,
) {

	/** 온보딩에 필요한 enum 타입별 선택 옵션 목록을 모두 내려준다. */
	@GetMapping("/options")
	fun getOptions(): ApiResponse<ProfileOptionsResponse> = ApiResponse.success(ProfileOptionsResponse.of())

	/** 현재 로그인 사용자의 프로필 정보를 조회한다. */
	@GetMapping
	fun getMyProfile(
		@LoginUser user: AuthUser,
	): ApiResponse<UserProfileResponse> =
		ApiResponse.success(UserProfileResponse.of(getUserDetailUseCase.getByUserId(user.id)))

	/**
	 * 현재 로그인 사용자의 프로필을 수정한다. (편집 가능 필드 전체 교체)
	 * 나이/성별/키/휴대폰번호/회사이메일은 변경할 수 없어 보존된다.
	 */
	@PutMapping
	fun updateMyProfile(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: UpdateProfileRequest,
	): ApiResponse<UserProfileResponse> =
		ApiResponse.success(UserProfileResponse.of(updateProfileUseCase.updateProfile(user.id, request.toCommand())))
}
