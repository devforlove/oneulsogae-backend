package com.org.meeple.infra.chat.adapter

import com.org.meeple.core.chat.application.port.out.GetChatMessagePort
import com.org.meeple.core.chat.application.port.out.SaveChatMessagePort
import com.org.meeple.core.chat.domain.ChatMessage
import com.org.meeple.core.chat.domain.ChatMessages
import com.org.meeple.infra.chat.entity.ChatMessageEntity
import com.org.meeple.infra.chat.mapper.toDomain
import com.org.meeple.infra.chat.mapper.toEntity
import com.org.meeple.infra.chat.repository.ChatMessageJpaRepository
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component

/**
 * core 모듈이 쓰는 [ChatMessageEntity]의 Spring Data 어댑터.
 * 저장([SaveChatMessagePort])·조회([GetChatMessagePort])를 파생 쿼리로 구현한다. (조인이 없어 QueryDSL 어댑터는 두지 않는다)
 */
@Component
class ChatMessageCoreAdapter(
	private val chatMessageJpaRepository: ChatMessageJpaRepository,
) : SaveChatMessagePort, GetChatMessagePort {

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(message: ChatMessage): ChatMessage =
		chatMessageJpaRepository.save(message.toEntity()).toDomain()

	// 커서 유무로 첫/다음 페이지 쿼리를 나눠 호출해 (chat_room_id, id) 인덱스 역방향 스캔이 깨지지 않게 한다.
	override fun findByChatRoom(chatRoomId: Long, beforeMessageId: Long?, limit: Int): ChatMessages {
		val entities: List<ChatMessageEntity> = if (beforeMessageId == null) {
			chatMessageJpaRepository.findByChatRoomIdOrderByIdDesc(chatRoomId, Limit.of(limit))
		} else {
			chatMessageJpaRepository.findByChatRoomIdAndIdLessThanOrderByIdDesc(chatRoomId, beforeMessageId, Limit.of(limit))
		}
		return ChatMessages(entities.map { entity: ChatMessageEntity -> entity.toDomain() })
	}
}
