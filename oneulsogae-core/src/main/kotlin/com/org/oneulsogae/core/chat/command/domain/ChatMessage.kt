package com.org.oneulsogae.core.chat.command.domain

import com.org.oneulsogae.common.chat.ChatMessageType
import com.org.oneulsogae.core.chat.ChatErrorCode
import com.org.oneulsogae.core.common.error.BusinessException
import java.time.LocalDateTime

/**
 * 채팅방에서 오간 메세지 도메인 모델.
 * 채팅방 행(json)에 묶지 않고 메세지마다 한 건씩 보관한다. (append 시 전체 재기록·동시 쓰기 유실 회피, 키셋 페이징 가능)
 * [chatRoomId]가 속한 채팅방, [senderId]가 보낸 사람, [content]가 본문, [sentAt]이 보낸(또는 생성된) 시각이다.
 * [type]이 SYSTEM(예: 상대방 나감 안내)이면 보낸 사람이 없어 [senderId]가 null이다.
 * 영속성은 [com.org.oneulsogae.infra.chat.command.entity.ChatMessageEntity]가 담당한다.
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
		 * [senderId]가 [chatRoomId] 채팅방에 보낼 신규 메세지를 생성한다.
		 * 본문을 검증(빈 값/최대 길이)한 뒤 [now]를 보낸 시각으로 둔다. (참가자/미종료 검증은 호출 측 책임)
		 */
		fun create(chatRoomId: Long, senderId: Long, content: String, now: LocalDateTime): ChatMessage {
			validateContent(content)
			return ChatMessage(chatRoomId = chatRoomId, senderId = senderId, content = content, sentAt = now)
		}

		/**
		 * 시스템(SYSTEM) 안내 메세지를 생성한다. (예: 상대 팀이 채팅방을 나감)
		 * 보낸 사람이 없어 [senderId]는 null이다. 본문을 검증(빈 값/최대 길이)한 뒤 [now]를 생성 시각으로 둔다.
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
				throw BusinessException(ChatErrorCode.EMPTY_MESSAGE)
			}
			if (content.length > MAX_CONTENT_LENGTH) {
				throw BusinessException(ChatErrorCode.MESSAGE_TOO_LONG)
			}
		}
	}
}
