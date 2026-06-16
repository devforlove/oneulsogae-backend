package com.org.meeple.infra.chat.adapter

import com.org.meeple.chatting.chat.application.port.out.UpdateChatRoomPort
import com.org.meeple.infra.chat.repository.ChatRoomJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * chatting 모듈이 쓰는 [com.org.meeple.infra.chat.entity.ChatRoomEntity]의 Spring Data 어댑터.
 * 발송 경로의 조건부 갱신([UpdateChatRoomPort])을 `ChatRoomJpaRepository`로 구현한다. (방을 로드하지 않는다)
 * 같은 엔티티의 core 포트는 [ChatRoomCoreAdapter]가 따로 구현한다.
 */
@Component
class ChatRoomChattingAdapter(
	private val chatRoomJpaRepository: ChatRoomJpaRepository,
) : UpdateChatRoomPort {

	override fun updateLastMessageIfActive(chatRoomId: Long, lastMessage: String, lastMessageAt: LocalDateTime): Boolean =
		chatRoomJpaRepository.updateLastMessageIfActive(chatRoomId, lastMessage, lastMessageAt) > 0
}
