package com.org.oneulsogae.api.lounge.request

/** 라운지 댓글 내용 수정 요청. */
data class UpdateLoungeCommentRequest(
	val content: String? = null,
)
