package com.org.oneulsogae.core.lounge.command.domain.event

/**
 * 라운지 글에 댓글(또는 대댓글)이 달렸음을 알리는 도메인 이벤트.
 * 수신측([com.org.oneulsogae.core.lounge.command.application.LoungeEventHandler])이 커밋 후 [receiverUserId]에게 알람을 저장한다.
 * 본인 글·본인 댓글에 단 경우(수신자 == 작성자)는 발행하지 않는다.
 */
data class LoungeCommentAdded(
	val commentId: Long,
	val postId: Long,
	/** 댓글을 단 사용자. (알람 문구·fromUserId) */
	val commentAuthorUserId: Long,
	/** 알람 수신자. 댓글이면 글 작성자, 대댓글이면 부모 댓글 작성자다. */
	val receiverUserId: Long,
	/** 대댓글 여부. 알람 유형·문구 분기에 쓴다. */
	val reply: Boolean,
)
