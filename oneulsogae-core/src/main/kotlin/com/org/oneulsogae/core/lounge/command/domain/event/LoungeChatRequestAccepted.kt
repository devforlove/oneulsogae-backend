package com.org.oneulsogae.core.lounge.command.domain.event

/**
 * 라운지 대화 신청이 수락됐음을 알리는 도메인 이벤트.
 * 수신측([com.org.oneulsogae.core.lounge.command.application.LoungeEventHandler])이 커밋 후 신청자에게 알람을 저장한다.
 */
data class LoungeChatRequestAccepted(
	val requestId: Long,
	/** 신청을 보낸 사용자. (알람 수신자) */
	val requesterUserId: Long,
	/** 신청을 수락한 글 작성자. (알람 문구·fromUserId) */
	val postAuthorUserId: Long,
	/** 수락으로 생성된 채팅방. (알람을 눌러 이동할 대상) */
	val chatRoomId: Long,
)
