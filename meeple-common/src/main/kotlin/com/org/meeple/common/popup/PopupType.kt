package com.org.meeple.common.popup

/** 팝업 유형. */
enum class PopupType(val description: String) {

	/** 일반 공지/이벤트 팝업. */
	NORMAL("일반"),

	/** 일일 보상 팝업. */
	DAILY_REWARD("일일 보상"),

	/** 소개팅 매칭 실패로 사용한 코인의 절반을 환불할 때 보여주는 팝업. */
	MATCH_FAILED_REFUND("매칭 실패 환불"),

	/** 신규 유저에게만 노출하는 팝업. (isNewUser=true인 요청에만 내려준다) */
	NEW_USER("신규 유저"),
}
