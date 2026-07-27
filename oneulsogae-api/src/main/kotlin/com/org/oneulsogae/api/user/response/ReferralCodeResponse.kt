package com.org.oneulsogae.api.user.response

import com.org.oneulsogae.core.user.query.dto.ReferralSummary

/** 내 추천 코드 조회 응답. 추천 실적(친구 수·받은 코인)을 함께 담는다. */
data class ReferralCodeResponse(
	val referralCode: String,
	/** 내 추천 코드로 가입을 완료한 친구 수. (탈퇴한 친구는 빠진다) */
	val referredUserCount: Int,
	/** 그 추천으로 내가 받은 코인 총량. 내가 가입할 때 남의 코드를 입력해 받은 보상은 포함하지 않는다. */
	val earnedCoinAmount: Int,
) {
	companion object {

		fun of(referralCode: String, summary: ReferralSummary): ReferralCodeResponse =
			ReferralCodeResponse(
				referralCode = referralCode,
				referredUserCount = summary.referredUserCount,
				earnedCoinAmount = summary.earnedCoinAmount,
			)
	}
}
