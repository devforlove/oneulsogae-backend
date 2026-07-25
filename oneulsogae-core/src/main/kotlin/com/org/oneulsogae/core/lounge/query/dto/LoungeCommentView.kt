package com.org.oneulsogae.core.lounge.query.dto

import java.time.LocalDateTime

/**
 * 라운지 댓글 한 건(read model). [parentCommentId]가 null이면 댓글(root), 값이 있으면 대댓글이다.
 * `author*`는 작성자 프로필(user_details)에서 조인해 온 표시용 값이며, 프로필이 없으면 null이다.
 * 삭제된 댓글([deleted]=true)은 [content]를 null로 마스킹해 내려준다. (클라이언트가 "삭제된 댓글입니다"로 표시)
 * [mine]은 조회한 사용자의 본인 댓글 여부로, 서비스가 채운다. (수정·삭제 버튼 노출 판단용)
 */
data class LoungeCommentView(
	val commentId: Long,
	val parentCommentId: Long?,
	val content: String?,
	val deleted: Boolean,
	val createdAt: LocalDateTime,
	val authorUserId: Long,
	val authorNickname: String?,
	val authorProfileImageCode: String?,
	val mine: Boolean = false,
) {
	/** dao 투영용 생성자. mine은 서비스가 채운다. */
	constructor(
		commentId: Long,
		parentCommentId: Long?,
		content: String?,
		deleted: Boolean,
		createdAt: LocalDateTime,
		authorUserId: Long,
		authorNickname: String?,
		authorProfileImageCode: String?,
	) : this(
		commentId, parentCommentId, content, deleted, createdAt,
		authorUserId, authorNickname, authorProfileImageCode, false,
	)

	/** 삭제된 댓글이면 내용을 null로 마스킹한 항목을 만든다. */
	fun maskedIfDeleted(): LoungeCommentView =
		if (deleted) copy(content = null) else this

	/** 조회한 사용자([viewerUserId]) 기준 본인 댓글 여부를 채운 항목을 만든다. */
	fun withMine(viewerUserId: Long?): LoungeCommentView =
		copy(mine = viewerUserId != null && authorUserId == viewerUserId)
}
