package com.org.oneulsogae.core.lounge.command.application.port.`in`

/** 라운지 댓글 내용 수정 유스케이스. (작성자 본인만) */
interface UpdateLoungeCommentUseCase {

	/** [userId]가 본인 댓글([commentId])의 내용을 [content]로 수정한다. */
	fun update(userId: Long, commentId: Long, content: String)
}
