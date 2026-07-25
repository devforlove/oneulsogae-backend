package com.org.oneulsogae.core.lounge.query.service.port.`in`

import com.org.oneulsogae.core.lounge.query.dto.LoungeCommentPage

/** 라운지 댓글 조회 유스케이스. root 댓글은 커서 기반으로 한 페이지씩, 그 페이지 root들의 대댓글은 전부 내려준다. */
interface GetLoungeCommentsUseCase {

	/**
	 * 글([postId])의 댓글 한 페이지를 오래된 순으로 조회한다. [cursor]를 주면 그보다 최신 구간을 잇는다.
	 * [userId]는 조회한 사용자로, 본인 댓글 여부(mine) 판정에만 쓴다. 비로그인(null)이면 mine은 모두 false다. (목록 자체는 공개)
	 */
	fun getComments(userId: Long?, postId: Long, cursor: Long?): LoungeCommentPage
}
