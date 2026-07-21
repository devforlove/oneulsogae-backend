package com.org.oneulsogae.common.lounge

/**
 * 라운지 셀소 대화 신청의 상태.
 * 신청은 [PENDING]으로 시작하고, 글 작성자가 수락하면 [ACCEPTED]가 되며 그때 채팅방이 생성된다.
 * 거절·만료는 두지 않는다(수락되지 않은 신청은 PENDING으로 남는다).
 */
enum class LoungeChatRequestStatus(val description: String) {

	/** 신청됨. 글 작성자가 아직 수락하지 않은 상태. */
	PENDING("신청됨"),

	/** 수락됨. 채팅방이 생성된 상태. */
	ACCEPTED("수락됨"),
}
