package com.org.oneulsogae.core.lounge.command.domain

import com.org.oneulsogae.common.lounge.LoungeChatRequestPolicy
import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import java.time.LocalDateTime

/**
 * 라운지 셀소 대화 신청 도메인 모델.
 * 신청자([requesterUserId])가 셀소 글([postId])의 작성자([receiverUserId])에게 대화를 신청한 한 건을 나타낸다.
 * 생성된 채팅방은 여기에 두지 않는다(`chat_rooms(match_type=LOUNGE, match_id=이 신청 id)`로 역참조한다).
 * [createdAt]은 영속성(BaseEntity)이 채우므로 저장 전(신규)에는 null이다.
 */
data class LoungeChatRequest(
	val id: Long = 0,
	val postId: Long,
	val requesterUserId: Long,
	/** 신청을 받은 사용자(글 작성자). 생성 시점에 확정되며 이후 바뀌지 않는다. */
	val receiverUserId: Long,
	val status: LoungeChatRequestStatus = LoungeChatRequestStatus.PENDING,
	val createdAt: LocalDateTime? = null,
) {

	/**
	 * 신청을 받은 글 작성자가 이 신청을 수락해 [LoungeChatRequestStatus.ACCEPTED]로 전이한 새 모델을 반환한다.
	 * - 수락자([actorUserId])가 수신자([receiverUserId])가 아니면 [LoungeErrorCode.LOUNGE_POST_NOT_OWNED]
	 * - 이미 수락한 신청이면 [LoungeErrorCode.LOUNGE_CHAT_REQUEST_ALREADY_ACCEPTED]
	 * - [now] 기준 만료된 신청이면 [LoungeErrorCode.LOUNGE_CHAT_REQUEST_EXPIRED] ([isExpired])
	 */
	fun acceptBy(actorUserId: Long, now: LocalDateTime): LoungeChatRequest {
		if (receiverUserId != actorUserId) {
			throw BusinessException(LoungeErrorCode.LOUNGE_POST_NOT_OWNED)
		}
		if (status == LoungeChatRequestStatus.ACCEPTED) {
			throw BusinessException(LoungeErrorCode.LOUNGE_CHAT_REQUEST_ALREADY_ACCEPTED)
		}
		if (isExpired(now)) {
			throw BusinessException(LoungeErrorCode.LOUNGE_CHAT_REQUEST_EXPIRED)
		}
		return copy(status = LoungeChatRequestStatus.ACCEPTED)
	}

	/**
	 * [now] 기준으로 만료된 신청인지 여부.
	 * 신청([createdAt])으로부터 [LoungeChatRequestPolicy.EXPIRATION](3일)이 지난 PENDING 신청만 만료로 본다.
	 * (이미 수락된 신청은 만료되지 않고, 저장 전이라 [createdAt]이 null이면 방금 만든 신청이므로 만료가 아니다)
	 */
	fun isExpired(now: LocalDateTime): Boolean =
		status == LoungeChatRequestStatus.PENDING &&
			createdAt != null &&
			!now.isBefore(createdAt.plus(LoungeChatRequestPolicy.EXPIRATION))

	companion object {

		/**
		 * 신규 대화 신청을 만든다. (PENDING 상태로 시작)
		 * - 본인이 작성한 글([postAuthorUserId]와 [requesterUserId]가 같음): [LoungeErrorCode.LOUNGE_CHAT_REQUEST_SELF]
		 * - 이성이 아님: [LoungeErrorCode.LOUNGE_CHAT_REQUEST_SAME_GENDER] ([validateOppositeGender])
		 *
		 * 본인 글 검사를 성별 검사보다 먼저 한다. (본인은 성별도 같으므로 순서가 바뀌면 엉뚱한 사유가 나간다)
		 */
		fun create(
			postId: Long,
			requesterUserId: Long,
			postAuthorUserId: Long,
			requesterGender: Gender?,
			postAuthorGender: Gender?,
		): LoungeChatRequest {
			if (requesterUserId == postAuthorUserId) {
				throw BusinessException(LoungeErrorCode.LOUNGE_CHAT_REQUEST_SELF)
			}
			validateOppositeGender(requesterGender, postAuthorGender)
			return LoungeChatRequest(postId = postId, requesterUserId = requesterUserId, receiverUserId = postAuthorUserId)
		}

		/**
		 * 이성에게만 신청할 수 있음을 검증한다.
		 * 성별이 같으면 물론이고, **둘 중 하나라도 확인되지 않으면(null) 이성임을 보장할 수 없으므로 함께 막는다.**
		 * (프로필이 없거나 온보딩이 끝나지 않은 비정상 상태라 통과시키면 동성 대화가 열릴 수 있다)
		 */
		fun validateOppositeGender(requesterGender: Gender?, postAuthorGender: Gender?) {
			if (requesterGender == null || postAuthorGender == null || requesterGender == postAuthorGender) {
				throw BusinessException(LoungeErrorCode.LOUNGE_CHAT_REQUEST_SAME_GENDER)
			}
		}
	}
}
