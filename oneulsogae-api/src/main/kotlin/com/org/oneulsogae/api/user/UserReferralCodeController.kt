package com.org.oneulsogae.api.user

import com.org.oneulsogae.api.user.response.ReferralCodeResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.user.command.application.port.`in`.IssueReferralCodeUseCase
import com.org.oneulsogae.core.user.query.service.port.`in`.GetReferralSummaryUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 추천 코드 엔드포인트. (인증 필요)
 * - GET /: 내 추천 코드와 추천 실적(친구 수·받은 코인)을 반환한다. 코드가 아직 없으면 발급(get-or-create)해 반환한다.
 *
 * 코드 발급은 쓰기라 command 유스케이스, 실적 집계는 조회라 query 유스케이스로 나눠 주입한다.
 */
@RestController
@RequestMapping("/users/v1/me/referral-code")
@Tag(name = "유저 추천 코드", description = "내 추천 코드 조회(없으면 발급)·추천 실적 엔드포인트 (인증 필요)")
class UserReferralCodeController(
	private val issueReferralCodeUseCase: IssueReferralCodeUseCase,
	private val getReferralSummaryUseCase: GetReferralSummaryUseCase,
) {

	/** 내 추천 코드와 추천 실적을 반환한다. 코드가 아직 없으면 발급해 저장 후 반환한다. (멱등) */
	@Operation(
		summary = "내 추천 코드·추천 실적 조회",
		description = "내 추천 코드를 반환한다. 아직 없으면 발급(get-or-create)해 반환한다. " +
			"referredUserCount는 내 추천 코드를 입력하고 가입을 완료한 친구 수(탈퇴한 친구는 빠진다), " +
			"earnedCoinAmount는 그 추천으로 내가 받은 코인 총량이다. " +
			"내가 가입할 때 남의 코드를 입력해 받은 보상은 earnedCoinAmount에 포함되지 않는다.",
	)
	@GetMapping
	fun getMyReferralCode(
		@LoginUser user: AuthUser,
	): ApiResponse<ReferralCodeResponse> =
		ApiResponse.success(
			ReferralCodeResponse.of(
				referralCode = issueReferralCodeUseCase.issue(user.id),
				summary = getReferralSummaryUseCase.getSummary(user.id),
			),
		)
}
