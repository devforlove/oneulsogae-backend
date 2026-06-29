package com.org.meeple.common.popup

/**
 * 팝업 유형.
 * [removeAfterView]가 true인 유형은 사용자가 한 번 조회하면 다시 노출하지 않도록 조회 시점에 제거(soft-delete)한다.
 * (환불 안내처럼 1회성 개인 알림에 해당)
 */
enum class PopupType(val description: String, val removeAfterView: Boolean) {

	/** 일반 공지/이벤트 팝업. */
	NORMAL("일반", false),

	/** 일일 보상 팝업. */
	DAILY_REWARD("일일 보상", false),

	/** 소개팅 매칭 실패로 사용한 코인의 절반을 환불할 때 보여주는 팝업. (1회 조회 후 제거) */
	MATCH_FAILED_REFUND("매칭 실패 환불", true),

	/** 미팅(팀) 매칭 실패로 사용한 코인의 절반을 환불할 때 보여주는 팝업. (1회 조회 후 제거) */
	MEETING_FAILED_REFUND("미팅 매칭 실패 환불", true),

	/** 신규 유저에게만 노출하는 팝업. (isNewUser=true인 요청에만 내려준다) */
	NEW_USER("신규 유저", false),
}
