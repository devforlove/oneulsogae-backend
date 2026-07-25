package com.org.oneulsogae.core.lounge.command.application.port.`in`.command

/** 라운지 댓글 작성 입력. [parentCommentId]가 null이면 댓글(root), 값이 있으면 그 댓글의 대댓글이다. */
data class WriteLoungeCommentCommand(
	val postId: Long,
	val parentCommentId: Long? = null,
	val content: String,
)
