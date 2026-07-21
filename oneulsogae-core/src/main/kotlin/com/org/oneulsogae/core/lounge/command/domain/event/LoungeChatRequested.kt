package com.org.oneulsogae.core.lounge.command.domain.event

/**
 * 라운지 셀소에 대화 신청이 생성됐음을 알리는 도메인 이벤트.
 * 수신측([com.org.oneulsogae.core.lounge.command.application.LoungeEventHandler])이 커밋 후 작성자에게 알람을 저장한다.
 */
data class LoungeChatRequested(
	val requestId: Long,
	/** 대화를 신청한 사용자. (알람 문구·fromUserId) */
	val requesterUserId: Long,
	/** 신청을 받은 글 작성자. (알람 수신자) */
	val postAuthorUserId: Long,
)
