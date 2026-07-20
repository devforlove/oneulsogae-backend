package com.org.oneulsogae.common.coin

/**
 * 코인 적립(획득) 유형. 무료 획득(DAILY)과 결제 구매(PURCHASE)를 구분한다.
 * 적립은 coins 원장에 양수 amount로 기록된다. (차감은 적립 유형이 아니라 [CoinUsageType]으로 구분한다)
 */
enum class CoinGetType(val description: String) {

	/** 무료로 획득한 코인. (출석/이벤트 등) */
	DAILY("출석 획득"),

	/** 결제로 구매한 코인. */
	PURCHASE("구매"),

	/** 소개팅 매칭 실패 등으로 사용한 코인의 일부를 되돌려준(환불) 코인. */
	REFUND("환불"),

	/** 신규 가입(온보딩 완료) 축하로 1회 지급하는 코인. */
	SIGNUP("가입 축하"),
}
