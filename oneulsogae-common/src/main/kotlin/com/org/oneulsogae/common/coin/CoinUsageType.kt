package com.org.oneulsogae.common.coin

import com.org.oneulsogae.common.user.Gender

/**
 * 코인을 사용(차감)하는 작업의 유형.
 * 소개팅(DATING)/미팅(MEETING) 각각에 대해 신청(INIT)과 수락(ACCEPT)을 구분한다.
 * 비용은 남녀가 다르다 — 남성은 [coinAmount], 여성은 그 절반([femaleAmount]). [coinAmount(Gender?)]로 얻는다.
 */
enum class CoinUsageType(val description: String, val coinAmount: Int, private val femaleAmount: Int) {

	/** 소개팅 신청. */
	DATING_INIT("소개팅 신청", 32, 16),

	/** 미팅 신청. */
	MEETING_INIT("미팅 신청", 40, 20),

	/** 소개팅 수락. */
	DATING_ACCEPT("소개팅 수락", 32, 16),

	/** 미팅 수락. */
	MEETING_ACCEPT("미팅 수락", 40, 20),

	/** 추가 소개(오늘의 추천 외 1명 더 소개받기). */
	EXTRA_INTRO("추가 소개", 30, 15),

	/** 라운지 셀소 대화 신청. */
	LOUNGE_CHAT_INIT("셀소 대화 신청", 32, 16),

	/** 라운지 셀소 대화 수락. */
	LOUNGE_CHAT_ACCEPT("셀소 대화 수락", 32, 16),
	;

	/** 성별별 비용. 여성은 절반, 남성·미상(null)은 기존 금액. (차감 경로의 null은 이론상 없고 fallback 안전장치) */
	fun coinAmount(gender: Gender?): Int =
		if (gender == Gender.FEMALE) femaleAmount else coinAmount
}
