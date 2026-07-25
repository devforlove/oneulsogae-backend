package com.org.oneulsogae.api.lounge.request

/** 라운지 댓글 작성 요청. [parentCommentId]가 null이면 댓글, 값이 있으면 그 댓글의 대댓글이다. */
data class WriteLoungeCommentRequest(
	val content: String? = null,
	val parentCommentId: Long? = null,
)
