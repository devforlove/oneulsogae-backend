package com.org.oneulsogae.api.user

import com.org.oneulsogae.api.user.response.ReferralCodeResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.user.command.application.port.`in`.IssueReferralCodeUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 추천 코드 엔드포인트. (인증 필요)
 * - GET /: 내 추천 코드를 반환한다. 아직 없으면 발급(get-or-create)해 반환한다.
 */
@RestController
@RequestMapping("/users/v1/me/referral-code")
@Tag(name = "유저 추천 코드", description = "내 추천 코드 조회(없으면 발급) 엔드포인트 (인증 필요)")
class UserReferralCodeController(
	private val issueReferralCodeUseCase: IssueReferralCodeUseCase,
) {

	/** 내 추천 코드를 반환한다. 아직 없으면 발급해 저장 후 반환한다. (멱등) */
	@Operation(summary = "내 추천 코드 조회", description = "내 추천 코드를 반환한다. 아직 없으면 발급(get-or-create)해 반환한다.")
	@GetMapping
	fun getMyReferralCode(
		@LoginUser user: AuthUser,
	): ApiResponse<ReferralCodeResponse> =
		ApiResponse.success(ReferralCodeResponse(issueReferralCodeUseCase.issue(user.id)))
}
