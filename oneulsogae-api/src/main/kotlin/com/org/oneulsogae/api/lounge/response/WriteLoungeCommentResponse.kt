package com.org.oneulsogae.api.lounge.response

import com.org.oneulsogae.core.lounge.command.application.port.`in`.result.WriteLoungeCommentResult

/** 라운지 댓글 작성 응답. */
data class WriteLoungeCommentResponse(
	val commentId: Long,
) {
	companion object {

		fun of(result: WriteLoungeCommentResult): WriteLoungeCommentResponse =
			WriteLoungeCommentResponse(commentId = result.commentId)
	}
}
