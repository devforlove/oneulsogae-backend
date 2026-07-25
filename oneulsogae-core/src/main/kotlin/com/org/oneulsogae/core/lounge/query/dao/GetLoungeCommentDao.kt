package com.org.oneulsogae.core.lounge.query.dao

import com.org.oneulsogae.core.lounge.query.dto.LoungeCommentView

/** 라운지 댓글 조회 dao. (조회 전용) */
interface GetLoungeCommentDao {

	/**
	 * 글의 root 댓글을 오래된(commentId 오름차순)순으로 최대 [limit]건 조회한다.
	 * [afterId]를 주면 그보다 최신(commentId 초과) 구간을 잇는다. (커서 페이징)
	 * 삭제된 root는 살아있는 대댓글이 남아 있을 때만 포함한다. ("삭제된 댓글" 표시용 — 대댓글까지 없으면 목록에서 뺀다)
	 */
	fun findRootPage(postId: Long, afterId: Long?, limit: Int): List<LoungeCommentView>

	/** [parentCommentIds]에 달린 대댓글을 오래된 순으로 전부 조회한다. 삭제된 대댓글은 제외한다. */
	fun findRepliesByParentIds(parentCommentIds: List<Long>): List<LoungeCommentView>
}
