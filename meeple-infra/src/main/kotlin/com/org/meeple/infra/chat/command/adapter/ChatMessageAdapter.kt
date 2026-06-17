package com.org.meeple.infra.chat.command.adapter

import com.org.meeple.chatting.chat.application.port.out.SaveChatMessagePort as ChattingSaveChatMessagePort
import com.org.meeple.chatting.chat.domain.ChatMessage as ChattingChatMessage
import com.org.meeple.core.chat.command.domain.ChatMessage
import com.org.meeple.core.chat.command.application.port.out.SaveChatMessagePort
import com.org.meeple.infra.chat.command.mapper.toChattingDomain
import com.org.meeple.infra.chat.command.mapper.toChattingEntity
import com.org.meeple.infra.chat.command.mapper.toDomain
import com.org.meeple.infra.chat.command.mapper.toEntity
import com.org.meeple.infra.chat.command.repository.ChatMessageJpaRepository
import org.springframework.stereotype.Component

/**
 * [com.org.meeple.infra.chat.command.entity.ChatMessageEntity]의 out-port 어댑터. (Spring Data 메서드 쿼리)
 * 같은 엔티티를 쓰는 core·chatting 모듈의 메세지 저장 out-port를 한 어댑터에서 함께 구현한다. (각 모듈의 도메인 모델·매퍼로 매핑)
 * 메세지 조회(키셋 페이지네이션)는 query 쪽 QueryDSL 구현체([GetChatMessageDaoImpl])가 따로 담당한다.
 */
@Component
class ChatMessageAdapter(
	private val chatMessageJpaRepository: ChatMessageJpaRepository,
) : SaveChatMessagePort, ChattingSaveChatMessagePort {

	// core: id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge).
	override fun save(message: ChatMessage): ChatMessage =
		chatMessageJpaRepository.save(message.toEntity()).toDomain()

	// chatting(발송 경로): chatting 자체 도메인 모델·매퍼로 저장한다.
	override fun save(message: ChattingChatMessage): ChattingChatMessage =
		chatMessageJpaRepository.save(message.toChattingEntity()).toChattingDomain()
}
