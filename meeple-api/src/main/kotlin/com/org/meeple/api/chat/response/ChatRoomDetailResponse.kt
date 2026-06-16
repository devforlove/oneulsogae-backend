package com.org.meeple.api.chat.response

import com.org.meeple.common.chat.ChatRoomStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.chat.query.dto.ChatMessageView
import com.org.meeple.core.chat.query.dto.ChatParticipant
import com.org.meeple.core.chat.query.dto.ChatRoomDetail
import java.time.LocalDateTime

/**
 * 채팅방 상세 응답. 메세지([messages])와, 첫 페이지에서만 채워지는 방 상태([status])·참여자([participants])를 내려준다.
 * 메세지는 id 오름차순(과거→최신) 순이며, 더 과거를 이어 읽을 때 [nextCursor](가장 오래된 메세지 id)를 `cursor`로 넘긴다. (없으면 null)
 * 커서로 이후 페이지를 조회하면 방·참여자 데이터는 다시 내려주지 않는다. ([status]는 null, [participants]는 빈 목록)
 */
data class ChatRoomDetailResponse(
	val chatRoomId: Long,
	/** 채팅방 상태. 첫 페이지에서만 내려가고, 커서로 이후 페이지를 조회하면 null. */
	val status: ChatRoomStatus?,
	val participants: List<ChatParticipantResponse>,
	val messages: List<ChatMessageResponse>,
	/** 다음(더 과거) 페이지 조회 커서. 가장 오래된 메세지의 id이며, 비어 있으면 null. */
	val nextCursor: Long?,
) {
	companion object {
		fun of(detail: ChatRoomDetail): ChatRoomDetailResponse =
			ChatRoomDetailResponse(
				chatRoomId = detail.chatRoomId,
				status = detail.status,
				participants = detail.participants.map { ChatParticipantResponse.of(it) },
				messages = detail.messages.values.map { ChatMessageResponse.of(it) },
				nextCursor = detail.messages.nextCursor,
			)
	}
}

/** 채팅방 참여자 정보. */
data class ChatParticipantResponse(
	val userId: Long,
	val nickname: String?,
	val profileImageCode: String?,
	/** 참가자 성별. 아직 설정되지 않았으면 null. */
	val gender: Gender?,
) {
	companion object {
		fun of(participant: ChatParticipant): ChatParticipantResponse =
			ChatParticipantResponse(
				userId = participant.userId,
				nickname = participant.nickname,
				profileImageCode = participant.profileImageCode,
				gender = participant.gender,
			)
	}
}

/** 채팅 메세지 한 건. */
data class ChatMessageResponse(
	val messageId: Long,
	val senderId: Long,
	val content: String,
	val sentAt: LocalDateTime,
) {
	companion object {
		fun of(message: ChatMessageView): ChatMessageResponse =
			ChatMessageResponse(
				messageId = message.id,
				senderId = message.senderId,
				content = message.content,
				sentAt = message.sentAt,
			)
	}
}
