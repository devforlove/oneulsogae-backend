package com.org.meeple.api.user

import com.org.meeple.api.match.request.UpdateProfileRequest
import com.org.meeple.api.match.response.ProfileOptionsResponse
import com.org.meeple.api.user.request.UpdateRefuseSameCompanyIntroRequest
import com.org.meeple.api.user.request.UpdateSecondaryEmailRequest
import com.org.meeple.api.user.response.UserProfileResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.matchuser.command.application.port.`in`.UpdateRefuseSameCompanyIntroUseCase
import com.org.meeple.core.user.query.service.port.`in`.GetUserDetailUseCase
import com.org.meeple.core.user.command.application.port.`in`.UpdateProfileUseCase
import com.org.meeple.core.user.command.application.port.`in`.UpdateSecondaryEmailUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users/v1/profile")
@Tag(name = "유저 프로필", description = "로그인 사용자의 프로필 조회 및 수정 엔드포인트")
class UserProfileController(
	private val getUserDetailUseCase: GetUserDetailUseCase,
	private val updateProfileUseCase: UpdateProfileUseCase,
	private val updateSecondaryEmailUseCase: UpdateSecondaryEmailUseCase,
	private val updateRefuseSameCompanyIntroUseCase: UpdateRefuseSameCompanyIntroUseCase,
	private val timeGenerator: TimeGenerator,
) {

	/** 온보딩에 필요한 enum 타입별 선택 옵션 목록을 모두 내려준다. (활동지역 목록은 GET /regions/v1) */
	@Operation(summary = "프로필 선택 옵션 조회", description = "온보딩에 필요한 enum 타입별 선택 옵션 목록을 모두 내려준다. 활동지역 목록은 GET /regions/v1에서 조회한다.")
	@GetMapping("/options")
	fun getOptions(): ApiResponse<ProfileOptionsResponse> = ApiResponse.success(ProfileOptionsResponse.of())

	/** 현재 로그인 사용자의 프로필 정보를 조회한다. */
	@Operation(summary = "내 프로필 조회", description = "현재 로그인 사용자의 프로필 정보를 조회한다.")
	@GetMapping
	fun getMyProfile(
		@LoginUser user: AuthUser,
	): ApiResponse<UserProfileResponse> =
		ApiResponse.success(UserProfileResponse.of(getUserDetailUseCase.getByUserId(user.id), timeGenerator.today()))

	/**
	 * 현재 로그인 사용자의 프로필을 수정한다. (편집 가능 필드 전체 교체)
	 * 나이/성별/키/휴대폰번호/회사이메일은 변경할 수 없어 보존된다.
	 */
	@Operation(summary = "내 프로필 수정", description = "편집 가능 필드를 전체 교체한다. 나이/성별/키/휴대폰번호/회사이메일은 변경할 수 없어 보존된다.")
	@PutMapping
	fun updateMyProfile(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: UpdateProfileRequest,
	): ApiResponse<UserProfileResponse> =
		ApiResponse.success(UserProfileResponse.of(updateProfileUseCase.updateProfile(user.id, request.toCommand()), timeGenerator.today()))

	/**
	 * 현재 로그인 사용자의 보조 이메일(마케팅·광고·매칭 알림 수신용)을 설정/변경/해제한다.
	 * secondaryEmail이 null이거나 공백이면 해제된다.
	 */
	@Operation(summary = "보조 이메일 설정", description = "마케팅·광고·매칭 알림 수신용 보조 이메일을 설정/변경한다. null 또는 공백이면 해제된다.")
	@PutMapping("/secondary-email")
	fun updateSecondaryEmail(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: UpdateSecondaryEmailRequest,
	): ApiResponse<UserProfileResponse> =
		ApiResponse.success(UserProfileResponse.of(updateSecondaryEmailUseCase.updateSecondaryEmail(user.id, request.secondaryEmail), timeGenerator.today()))

	/**
	 * 현재 로그인 사용자의 같은 회사 구성원 소개 거부 플래그를 변경한다.
	 * 매칭 읽기 모델(match_user)에 적재되지 않은 사용자(매칭 프로필 미완성)는 400.
	 */
	@Operation(summary = "같은 회사 소개 거부 설정", description = "같은 회사 구성원에게 소개(추천)되는 것을 거부할지 설정한다. 매칭 프로필 미완성 사용자는 400.")
	@PutMapping("/refuse-same-company-intro")
	fun updateRefuseSameCompanyIntro(
		@LoginUser user: AuthUser,
		@Valid @RequestBody request: UpdateRefuseSameCompanyIntroRequest,
	): ApiResponse<Unit> {
		updateRefuseSameCompanyIntroUseCase.updateRefuseSameCompanyIntro(user.id, request.refuseSameCompanyIntro)
		return ApiResponse.success()
	}
}
