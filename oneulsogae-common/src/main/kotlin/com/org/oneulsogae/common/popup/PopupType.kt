package com.org.oneulsogae.common.popup

/**
 * 팝업 유형.
 * [removeAfterView]가 true인 유형은 사용자가 한 번 조회하면 다시 노출하지 않도록 조회 시점에 제거(soft-delete)한다.
 * (환불 안내처럼 1회성 개인 알림에 해당)
 * [singlePerUser]가 true인 유형은 한 응답에 한 건만 노출한다. 여러 건이 노출 대상이면 앞선 한 건만 남겨,
 * 한 번에 팝업이 쏟아지지 않게 한다. (남은 건은 사라지지 않고 다음 조회에서 이어 노출된다)
 */
enum class PopupType(val description: String, val removeAfterView: Boolean, val singlePerUser: Boolean) {

	/** 일반 공지/이벤트 팝업. (공지는 여러 건 동시 노출이 정상이라 중복을 줄이지 않는다) */
	NORMAL("일반", false, false),

	/** 일일 보상 팝업. */
	DAILY_REWARD("일일 보상", false, true),

	/** 소개팅 매칭 실패로 사용한 코인의 절반을 환불할 때 보여주는 팝업. (1회 조회 후 제거) */
	MATCH_FAILED_REFUND("매칭 실패 환불", true, true),

	/** 미팅(팀) 매칭 실패로 사용한 코인의 절반을 환불할 때 보여주는 팝업. (1회 조회 후 제거) */
	MEETING_FAILED_REFUND("미팅 매칭 실패 환불", true, true),

	/** 신규 유저에게만 노출하는 팝업. (isNewUser=true인 요청에만 내려준다) */
	NEW_USER("신규 유저", false, true),
}
