package com.org.oneulsogae.common.coin

/**
 * 코인을 사용(차감)하는 작업의 유형.
 * 소개팅(DATING)/미팅(MEETING) 각각에 대해 신청(INIT)과 수락(ACCEPT)을 구분한다.
 * 각 작업 수행에 필요한 코인 개수([coinAmount])를 함께 가진다.
 */
enum class CoinUsageType(val description: String, val coinAmount: Int) {

	/** 소개팅 신청. */
	DATING_INIT("소개팅 신청", 32),

	/** 미팅 신청. */
	MEETING_INIT("미팅 신청", 40),

	/** 소개팅 수락. */
	DATING_ACCEPT("소개팅 수락", 32),

	/** 미팅 수락. */
	MEETING_ACCEPT("미팅 수락", 40),

	/** 추가 소개(오늘의 추천 외 1명 더 소개받기). */
	EXTRA_INTRO("추가 소개", 30),

	/** 라운지 셀소 대화 신청. */
	LOUNGE_CHAT_INIT("셀소 대화 신청", 32),

	/** 라운지 셀소 대화 수락. */
	LOUNGE_CHAT_ACCEPT("셀소 대화 수락", 32),
}
