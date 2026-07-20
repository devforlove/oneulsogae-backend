package com.org.oneulsogae.chatting.chat.domain

import com.org.oneulsogae.chatting.chat.application.ChatErrorCode
import com.org.oneulsogae.chatting.common.error.ChatException
import com.org.oneulsogae.common.chat.ChatMessageType
import java.time.LocalDateTime

/**
 * 채팅방에서 오간 메세지 도메인 모델. (chatting 모듈 자체 모델)
 * 영속성(`ChatMessageEntity`)·테이블은 core/HTTP 측과 공유하지만, chatting은 core 도메인에 의존하지 않고 자체 모델로 다룬다.
 * [chatRoomId]가 속한 채팅방, [senderId]가 보낸 사람, [content]가 본문, [sentAt]이 보낸(또는 생성된) 시각이다.
 * [type]이 SYSTEM(예: 상대방 나감 안내)이면 보낸 사람이 없어 [senderId]가 null이다.
 */
data class ChatMessage(
	val id: Long = 0,
	val chatRoomId: Long,
	val senderId: Long?,
	val content: String,
	val type: ChatMessageType = ChatMessageType.USER,
	val sentAt: LocalDateTime,
) {

	companion object {

		/** 메세지 본문 최대 길이. (chat_rooms.last_message 컬럼 길이와 맞춘다) */
		const val MAX_CONTENT_LENGTH: Int = 1000

		/**
		 * [senderId]가 [chatRoomId] 채팅방에 보낼 신규 메세지를 생성한다. (USER 유형)
		 * 본문을 검증(빈 값/최대 길이)한 뒤 [now]를 보낸 시각으로 둔다. (참가/미종료 검증은 호출 측 책임)
		 */
		fun create(chatRoomId: Long, senderId: Long, content: String, now: LocalDateTime): ChatMessage {
			validateContent(content)
			return ChatMessage(chatRoomId = chatRoomId, senderId = senderId, content = content, sentAt = now)
		}

		/**
		 * [chatRoomId] 채팅방에 시스템 안내 메세지(SYSTEM)를 생성한다. (예: 상대방 나감)
		 * 보낸 사람이 없으므로 senderId는 null이다. 본문은 시스템이 만들지만 동일 규칙(빈 값/길이)으로 검증한다.
		 */
		fun createSystem(chatRoomId: Long, content: String, now: LocalDateTime): ChatMessage {
			validateContent(content)
			return ChatMessage(
				chatRoomId = chatRoomId,
				senderId = null,
				content = content,
				type = ChatMessageType.SYSTEM,
				sentAt = now,
			)
		}

		private fun validateContent(content: String) {
			if (content.isBlank()) {
				throw ChatException(ChatErrorCode.EMPTY_MESSAGE)
			}
			if (content.length > MAX_CONTENT_LENGTH) {
				throw ChatException(ChatErrorCode.MESSAGE_TOO_LONG)
			}
		}
	}
}
