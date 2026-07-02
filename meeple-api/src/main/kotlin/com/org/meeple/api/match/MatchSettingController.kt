package com.org.meeple.api.match

import com.org.meeple.api.match.request.UpdateRefuseSameCompanyIntroRequest
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.matchuser.command.application.port.`in`.UpdateRefuseSameCompanyIntroUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 매칭 설정 엔드포인트. (모두 인증 필요)
 * 매칭 읽기 모델(match_user)이 보관하는 사용자별 매칭 설정을 변경한다.
 */
@Tag(name = "매칭 설정", description = "사용자별 매칭 설정 엔드포인트. 같은 회사 소개 거부 설정을 제공한다.")
@RestController
@RequestMapping("/matches/v1/settings")
class MatchSettingController(
	private val updateRefuseSameCompanyIntroUseCase: UpdateRefuseSameCompanyIntroUseCase,
) {

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
