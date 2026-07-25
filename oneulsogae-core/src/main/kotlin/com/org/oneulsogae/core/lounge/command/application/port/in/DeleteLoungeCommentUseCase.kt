package com.org.oneulsogae.core.lounge.command.application.port.`in`

/** 라운지 댓글 삭제 유스케이스. (작성자 본인만, soft delete) */
interface DeleteLoungeCommentUseCase {

	/** [userId]가 본인 댓글([commentId])을 삭제한다. */
	fun delete(userId: Long, commentId: Long)
}
