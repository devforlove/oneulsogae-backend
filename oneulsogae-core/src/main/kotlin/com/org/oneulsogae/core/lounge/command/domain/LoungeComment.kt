package com.org.oneulsogae.core.lounge.command.domain

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import java.time.LocalDateTime

/**
 * 라운지 글 댓글 도메인 모델. 라운지 글([LoungePost])에 [postId]로 붙는다.
 * [parentId]가 null이면 댓글(root), 값이 있으면 그 댓글에 단 대댓글이다. 깊이는 1단계까지만 허용한다.
 * 삭제는 soft delete로 다루며, 삭제된 댓글([deleted])은 수정·삭제·답글 대상이 될 수 없다.
 * (살아있는 대댓글이 남은 삭제 댓글은 조회 화면에 "삭제된 댓글"로 계속 노출되므로 행 자체는 유지한다)
 */
data class LoungeComment(
	val id: Long = 0,
	val postId: Long,
	val userId: Long,
	/** 부모 댓글 id. null이면 댓글(root), 값이 있으면 대댓글이다. */
	val parentId: Long? = null,
	val content: String,
	/** soft delete 여부. 삭제된 댓글은 수정·삭제·답글 대상이 될 수 없다. */
	val deleted: Boolean = false,
	val createdAt: LocalDateTime? = null,
) {

	/**
	 * 작성자 본인이 내용을 [newContent]로 수정한 새 모델을 반환한다.
	 * - 삭제된 댓글: [LoungeErrorCode.LOUNGE_COMMENT_NOT_FOUND]
	 * - 본인 댓글이 아님: [LoungeErrorCode.LOUNGE_COMMENT_NOT_OWNED]
	 * - 내용이 비었거나 최대 길이 초과: [LoungeErrorCode.LOUNGE_COMMENT_INVALID_CONTENT]
	 */
	fun editBy(actorUserId: Long, newContent: String): LoungeComment {
		validateOwnedBy(actorUserId)
		validateContent(newContent)
		return copy(content = newContent)
	}

	/**
	 * 작성자 본인만 수정·삭제할 수 있음을 검증한다.
	 * - 삭제된 댓글: [LoungeErrorCode.LOUNGE_COMMENT_NOT_FOUND] (이미 지워진 대상은 없는 것으로 다룬다)
	 * - 본인 댓글이 아님: [LoungeErrorCode.LOUNGE_COMMENT_NOT_OWNED]
	 */
	fun validateOwnedBy(actorUserId: Long) {
		if (deleted) {
			throw BusinessException(LoungeErrorCode.LOUNGE_COMMENT_NOT_FOUND)
		}
		if (userId != actorUserId) {
			throw BusinessException(LoungeErrorCode.LOUNGE_COMMENT_NOT_OWNED)
		}
	}

	companion object {

		/** 댓글 내용의 최대 길이. */
		const val MAX_CONTENT_LENGTH: Int = 500

		/**
		 * 내용과 부모 댓글을 검증해 신규 댓글(또는 대댓글)을 만든다. [parent]가 null이면 댓글(root)이다.
		 * - 내용이 비었거나 최대 길이 초과: [LoungeErrorCode.LOUNGE_COMMENT_INVALID_CONTENT]
		 * - 부모가 삭제됐거나 다른 글의 댓글: [LoungeErrorCode.LOUNGE_COMMENT_NOT_FOUND]
		 * - 부모가 이미 대댓글(깊이 1단계 초과): [LoungeErrorCode.LOUNGE_COMMENT_REPLY_DEPTH_EXCEEDED]
		 */
		fun create(postId: Long, userId: Long, content: String, parent: LoungeComment?): LoungeComment {
			validateContent(content)
			if (parent != null) {
				if (parent.deleted || parent.postId != postId) {
					throw BusinessException(LoungeErrorCode.LOUNGE_COMMENT_NOT_FOUND)
				}
				if (parent.parentId != null) {
					throw BusinessException(LoungeErrorCode.LOUNGE_COMMENT_REPLY_DEPTH_EXCEEDED)
				}
			}
			return LoungeComment(postId = postId, userId = userId, parentId = parent?.id, content = content)
		}

		/**
		 * 댓글 내용 검증. 비었거나 [MAX_CONTENT_LENGTH]를 넘으면
		 * [LoungeErrorCode.LOUNGE_COMMENT_INVALID_CONTENT].
		 */
		fun validateContent(content: String) {
			if (content.isBlank() || content.length > MAX_CONTENT_LENGTH) {
				throw BusinessException(LoungeErrorCode.LOUNGE_COMMENT_INVALID_CONTENT)
			}
		}
	}
}
