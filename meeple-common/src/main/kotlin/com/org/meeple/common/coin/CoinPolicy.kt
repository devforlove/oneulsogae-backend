package com.org.meeple.common.coin

/** 코인 적립/사용 정책 상수. */
object CoinPolicy {

	/** 출석(DAILY) 보상으로 하루 1회 지급하는 코인 수량. */
	const val DAILY_REWARD_COIN_AMOUNT: Int = 1

	/** 신규 가입(온보딩 완료) 축하로 1회 지급하는 코인 수량. */
	const val SIGNUP_REWARD_COIN_AMOUNT: Int = 100
}
