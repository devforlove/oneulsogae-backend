package com.org.oneulsogae.core.lounge.command.application.port.out

import com.org.oneulsogae.core.lounge.command.domain.LoungeComment

/** 라운지 댓글 단건 조회 out-port. */
interface GetLoungeCommentPort {

	/**
	 * 댓글 한 건. 없으면 null.
	 * 삭제(soft delete)된 행도 [LoungeComment.deleted]=true로 반환한다 — 삭제 여부 판정은 도메인이 한다.
	 */
	fun findById(commentId: Long): LoungeComment?
}
