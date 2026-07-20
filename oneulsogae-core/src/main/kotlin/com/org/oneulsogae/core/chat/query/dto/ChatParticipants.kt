package com.org.oneulsogae.core.chat.query.dto

import com.org.oneulsogae.core.chat.ChatErrorCode
import com.org.oneulsogae.core.common.error.BusinessException

/**
 * 한 채팅방의 참가자([ChatParticipant]) 목록의 일급 컬렉션(first-class collection).
 * 참가자 식별·프로필(닉네임·이미지·성별)을 함께 담은 read model의 모음으로, 참가 검증을 한곳에 응집한다.
 * 나감 여부로 거르지 않고 참가 이력이 있는 전원을 담는다. (목록/상세 공통 정책)
 */
data class ChatParticipants(
	val values: List<ChatParticipant>,
) {

	/** 참가자 수. */
	val size: Int
		get() = values.size

	/** 비어 있는지 여부. */
	fun isEmpty(): Boolean = values.isEmpty()

	/**
	 * [userId]가 이 채팅방의 참가자인지 검증한다.
	 * 참가자가 아니면 [ChatErrorCode.NOT_CHAT_ROOM_PARTICIPANT]를 던진다. (나감 여부는 보지 않는다)
	 */
	fun validateParticipant(userId: Long) {
		if (values.none { it.userId == userId }) {
			throw BusinessException(ChatErrorCode.NOT_CHAT_ROOM_PARTICIPANT)
		}
	}
}
