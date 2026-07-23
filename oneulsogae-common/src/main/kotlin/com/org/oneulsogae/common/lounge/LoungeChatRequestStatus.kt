package com.org.oneulsogae.common.lounge

/**
 * 라운지 셀소 대화 신청의 상태.
 * 신청은 [PENDING]으로 시작하고, 글 작성자가 수락하면 [ACCEPTED]가 되며 그때 채팅방이 생성된다.
 * 거절 상태는 두지 않으며, 만료도 상태가 아니라 신청 시 계산된 만료 시각(expired_at) 경과로 판정한다.
 * (수락되지 않은 신청은 PENDING으로 남고, 만료된 PENDING은 수락 불가·목록 제외로만 다룬다)
 */
enum class LoungeChatRequestStatus(val description: String) {

	/** 신청됨. 글 작성자가 아직 수락하지 않은 상태. */
	PENDING("신청됨"),

	/** 수락됨. 채팅방이 생성된 상태. */
	ACCEPTED("수락됨"),
}
