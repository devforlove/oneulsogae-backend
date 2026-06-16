package com.org.meeple.infra.chat.adapter

import com.org.meeple.chatting.chat.application.port.out.SaveChatMessagePort
import com.org.meeple.chatting.chat.domain.ChatMessage
import com.org.meeple.infra.chat.mapper.toChattingDomain
import com.org.meeple.infra.chat.mapper.toChattingEntity
import com.org.meeple.infra.chat.repository.ChatMessageJpaRepository
import org.springframework.stereotype.Component

/**
 * chatting 모듈이 쓰는 [com.org.meeple.infra.chat.entity.ChatMessageEntity]의 Spring Data 어댑터.
 * 메세지 저장([SaveChatMessagePort])을 `ChatMessageJpaRepository`로 구현한다.
 * 같은 엔티티의 core 포트(조회 포함)는 [ChatMessageCoreAdapter]가 따로 구현한다.
 */
@Component
class ChatMessageChattingAdapter(
	private val chatMessageJpaRepository: ChatMessageJpaRepository,
) : SaveChatMessagePort {

	override fun save(message: ChatMessage): ChatMessage =
		chatMessageJpaRepository.save(message.toChattingEntity()).toChattingDomain()
}
