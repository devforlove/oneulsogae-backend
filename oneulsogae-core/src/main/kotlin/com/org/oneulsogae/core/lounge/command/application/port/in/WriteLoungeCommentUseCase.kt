package com.org.oneulsogae.core.lounge.command.application.port.`in`

import com.org.oneulsogae.core.lounge.command.application.port.`in`.command.WriteLoungeCommentCommand
import com.org.oneulsogae.core.lounge.command.application.port.`in`.result.WriteLoungeCommentResult

/** 라운지 글에 댓글(또는 대댓글)을 작성하는 유스케이스. */
interface WriteLoungeCommentUseCase {

	/** [userId]가 댓글을 작성한다. 성공하면 생성된 댓글 식별자를 반환한다. */
	fun write(userId: Long, command: WriteLoungeCommentCommand): WriteLoungeCommentResult
}
