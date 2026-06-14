package com.org.meeple.common.coin

/**
 * 코인 거래 유형. 적립(FREE/PURCHASE)과 차감(SPEND)을 구분한다.
 * coins 원장에서 적립은 양수 amount, 차감은 음수 amount로 기록되어 합(SUM)이 곧 잔액이 된다.
 */
enum class CoinGetType(val description: String, val isSpending: Boolean) {

	/** 무료로 획득한 코인. (출석/이벤트 등) */
	DAILY("출석 획득", false),

	/** 결제로 구매한 코인. */
	PURCHASE("구매", false),

	/** 사용(차감)된 코인. coins 원장에는 음수 amount로 기록된다. */
	SPEND("사용 차감", true),
}
