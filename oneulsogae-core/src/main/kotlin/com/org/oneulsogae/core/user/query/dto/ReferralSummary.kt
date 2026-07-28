package com.org.oneulsogae.core.user.query.dto

import com.org.oneulsogae.common.coin.CoinPolicy

/**
 * 내 추천 실적 read model. 추천 코드 화면의 "추천한 친구 N명 / 받은 코인 M개" 표시에 쓴다.
 *
 * [earnedCoinAmount]는 **내가 추천인으로서 받은** 코인만 센다. 내가 가입할 때 남의 코드를 입력해 받은 보상은 빼며,
 * 그래서 coin_histories 합계(REFERRAL 전액)가 아니라 추천 인원수에 보상 단가를 곱해 만든다.
 * (coin_histories만으로는 같은 REFERRAL 적립이 '내가 추천해서 받은 건'인지 '내가 추천받아서 받은 건'인지 구분되지 않는다)
 */
data class ReferralSummary(
	/** 내 추천 코드로 가입을 완료한 친구 수. */
	val referredUserCount: Int,
	/** 그 추천으로 내가 받은 코인 총량. */
	val earnedCoinAmount: Int,
) {
	companion object {

		// ponytail: 보상 단가가 고정(CoinPolicy.REFERRAL_REWARD_COIN_AMOUNT)이라 인원수 × 단가로 충분하다.
		// 단가를 바꾸면 과거 추천 건에도 새 단가가 소급 적용된다 — 지급액을 건별로 남겨야 할 때 referral_rewards 테이블을 도입한다.
		fun of(referredUserCount: Int): ReferralSummary =
			ReferralSummary(
				referredUserCount = referredUserCount,
				earnedCoinAmount = referredUserCount * CoinPolicy.REFERRAL_REWARD_COIN_AMOUNT,
			)
	}
}
