package com.org.oneulsogae.core.lounge.command.domain

import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import java.time.LocalDateTime

/**
 * 라운지 셀소 대화 신청 도메인 모델.
 * 신청자([requesterUserId])가 셀소 글([postId])의 작성자에게 대화를 신청한 한 건을 나타낸다.
 * 글 작성자는 `lounge_posts.user_id`가 단일 진실원천이라 여기에 복사해 두지 않고, 규칙 판정 시 파라미터로 받는다.
 * 생성된 채팅방도 여기에 두지 않는다(`chat_rooms(match_type=LOUNGE, match_id=이 신청 id)`로 역참조한다).
 * [createdAt]은 영속성(BaseEntity)이 채우므로 저장 전(신규)에는 null이다.
 */
data class LoungeChatRequest(
	val id: Long = 0,
	val postId: Long,
	val requesterUserId: Long,
	val status: LoungeChatRequestStatus = LoungeChatRequestStatus.PENDING,
	val createdAt: LocalDateTime? = null,
) {

	/**
	 * 글 작성자([postAuthorUserId])가 이 신청을 수락해 [LoungeChatRequestStatus.ACCEPTED]로 전이한 새 모델을 반환한다.
	 * - 수락자([actorUserId])가 글 작성자가 아니면 [LoungeErrorCode.LOUNGE_POST_NOT_OWNED]
	 * - 이미 수락한 신청이면 [LoungeErrorCode.LOUNGE_CHAT_REQUEST_ALREADY_ACCEPTED]
	 */
	fun acceptBy(postAuthorUserId: Long, actorUserId: Long): LoungeChatRequest {
		if (postAuthorUserId != actorUserId) {
			throw BusinessException(LoungeErrorCode.LOUNGE_POST_NOT_OWNED)
		}
		if (status == LoungeChatRequestStatus.ACCEPTED) {
			throw BusinessException(LoungeErrorCode.LOUNGE_CHAT_REQUEST_ALREADY_ACCEPTED)
		}
		return copy(status = LoungeChatRequestStatus.ACCEPTED)
	}

	companion object {

		/**
		 * 신규 대화 신청을 만든다. (PENDING 상태로 시작)
		 * 본인이 작성한 글([postAuthorUserId]와 [requesterUserId]가 같음)에는 신청할 수 없다.
		 * ([LoungeErrorCode.LOUNGE_CHAT_REQUEST_SELF])
		 */
		fun create(postId: Long, requesterUserId: Long, postAuthorUserId: Long): LoungeChatRequest {
			if (requesterUserId == postAuthorUserId) {
				throw BusinessException(LoungeErrorCode.LOUNGE_CHAT_REQUEST_SELF)
			}
			return LoungeChatRequest(postId = postId, requesterUserId = requesterUserId)
		}
	}
}
