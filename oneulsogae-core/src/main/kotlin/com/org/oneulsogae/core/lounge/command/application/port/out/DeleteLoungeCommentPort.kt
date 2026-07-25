package com.org.oneulsogae.core.lounge.command.application.port.out

import java.time.LocalDateTime

/** 라운지 댓글 삭제(soft delete) out-port. */
interface DeleteLoungeCommentPort {

	/** 댓글을 [now] 시각으로 soft delete한다. 이미 없거나 삭제된 행이면 아무것도 하지 않는다. */
	fun delete(commentId: Long, now: LocalDateTime)
}
