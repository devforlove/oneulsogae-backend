package com.org.oneulsogae.core.lounge.query.dto

/**
 * 라운지 댓글([LoungeCommentView])의 커서 페이지(일급 컬렉션).
 * root 댓글만 커서 페이징하고, 현재 페이지 root들의 대댓글([replies])은 전부 함께 담는다.
 * 커서 산출 규칙(오래된 순, 마지막 root의 commentId)을 한곳에 응집시킨다.
 */
class LoungeCommentPage private constructor(
	/** 현재 페이지의 root 댓글 목록. 오래된 순(commentId 오름차순). */
	val values: List<LoungeCommentView>,
	/** 현재 페이지 root들에 달린 대댓글 전부. 오래된 순. 서비스가 채운다. */
	val replies: List<LoungeCommentView>,
	/** 다음(더 최신) 페이지가 있는지 여부. */
	val hasNext: Boolean,
) {

	/** 다음 페이지 조회의 기준 커서. 현재 페이지 마지막 root의 commentId이며, 다음 페이지가 없으면 null. */
	val nextCursor: Long?
		get() = if (hasNext) values.lastOrNull()?.commentId else null

	/** 현재 페이지 root들의 대댓글을 채운 페이지를 만든다. */
	fun withReplies(replies: List<LoungeCommentView>): LoungeCommentPage =
		LoungeCommentPage(values = values, replies = replies, hasNext = hasNext)

	/** 조회한 사용자([viewerUserId]) 기준 본인 댓글 여부(mine)를 root·대댓글 모두에 채운 페이지를 만든다. */
	fun withMine(viewerUserId: Long?): LoungeCommentPage =
		LoungeCommentPage(
			values = values.map { view: LoungeCommentView -> view.withMine(viewerUserId) },
			replies = replies.map { view: LoungeCommentView -> view.withMine(viewerUserId) },
			hasNext = hasNext,
		)

	companion object {

		/**
		 * "한 건 더 읽기(size + 1)"로 조회한 root 행들로 페이지를 만든다.
		 * [rows]가 [size]보다 많으면 다음 페이지가 있는 것으로 보고 초과분은 잘라내며,
		 * 삭제된 root의 내용은 마스킹한다. ([LoungeCommentView.maskedIfDeleted])
		 */
		fun of(rows: List<LoungeCommentView>, size: Int): LoungeCommentPage =
			LoungeCommentPage(
				values = rows.take(size).map { view: LoungeCommentView -> view.maskedIfDeleted() },
				replies = emptyList(),
				hasNext = rows.size > size,
			)
	}
}
