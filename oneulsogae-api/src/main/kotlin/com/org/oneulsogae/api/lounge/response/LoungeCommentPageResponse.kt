package com.org.oneulsogae.api.lounge.response

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.lounge.query.dto.LoungeCommentPage
import com.org.oneulsogae.core.lounge.query.dto.LoungeCommentView
import java.time.LocalDateTime

/**
 * 라운지 댓글 목록 응답. root 댓글(오래된 순) 한 페이지와 각 root에 중첩된 대댓글 전부를 담는다.
 * 다음 페이지는 [nextCursor]를 cursor 파라미터로 그대로 넘겨 조회한다. ([hasNext]=false면 마지막 페이지)
 */
data class LoungeCommentPageResponse(
	val comments: List<LoungeCommentResponse>,
	val hasNext: Boolean,
	val nextCursor: Long?,
) {
	companion object {

		fun of(page: LoungeCommentPage): LoungeCommentPageResponse {
			val repliesByParent: Map<Long?, List<LoungeCommentView>> =
				page.replies.groupBy { reply: LoungeCommentView -> reply.parentCommentId }
			return LoungeCommentPageResponse(
				comments = page.values.map { root: LoungeCommentView ->
					LoungeCommentResponse.of(root, repliesByParent[root.commentId].orEmpty())
				},
				hasNext = page.hasNext,
				nextCursor = page.nextCursor,
			)
		}
	}
}

/**
 * 라운지 댓글 한 건 응답. 삭제된 댓글([deleted]=true)은 [content]가 null이다. ("삭제된 댓글입니다"로 표시)
 * [mine]은 조회한 사용자의 본인 댓글 여부다. (수정·삭제 버튼 노출 판단용, 비로그인이면 항상 false)
 */
data class LoungeCommentResponse(
	val commentId: Long,
	val content: String?,
	val deleted: Boolean,
	val createdAt: LocalDateTime,
	val authorNickname: String?,
	/** 작성자 성별. 아바타가 성별+아바타 번호 조합으로 그려져 함께 내려준다. (프로필 미설정이면 null) */
	val authorGender: Gender?,
	val authorProfileImageCode: String?,
	val mine: Boolean,
	/** 이 댓글에 달린 대댓글(오래된 순). 대댓글 항목에서는 항상 빈 목록이다. */
	val replies: List<LoungeCommentResponse> = emptyList(),
) {
	companion object {

		fun of(view: LoungeCommentView, replies: List<LoungeCommentView>): LoungeCommentResponse =
			LoungeCommentResponse(
				commentId = view.commentId,
				content = view.content,
				deleted = view.deleted,
				createdAt = view.createdAt,
				authorNickname = view.authorNickname,
				authorGender = view.authorGender,
				authorProfileImageCode = view.authorProfileImageCode,
				mine = view.mine,
				replies = replies.map { reply: LoungeCommentView -> of(reply, emptyList()) },
			)
	}
}
