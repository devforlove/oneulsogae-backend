package com.org.oneulsogae.core.user.query.dto

/**
 * 내 추천 실적 read model. 추천 코드 화면의 "추천한 친구 N명 / 받은 코인 M개" 표시에 쓴다.
 *
 * 집계 소스는 추천 보상 지급 이력(referral_reward_grants)이다. 실제 지급된 건만 세므로
 * 화면의 인원수와 코인이 항상 맞고, 친구가 탈퇴해도 이미 받은 보상이 사라지지 않는다.
 * [earnedCoinAmount]는 지급 시점 금액의 합이라 보상 단가가 바뀌어도 과거 건이 소급되지 않는다.
 * (내가 가입할 때 남의 코드를 입력해 받은 보상은 내가 추천인이 아니므로 자연히 빠진다)
 */
data class ReferralSummary(
	/** 내 추천으로 보상이 지급된 친구 수. */
	val referredUserCount: Int,
	/** 그 추천으로 내가 받은 코인 총량. */
	val earnedCoinAmount: Int,
)
