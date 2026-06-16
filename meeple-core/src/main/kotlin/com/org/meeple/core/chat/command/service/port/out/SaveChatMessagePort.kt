package com.org.meeple.core.chat.command.service.port.out

import com.org.meeple.core.chat.command.domain.ChatMessage

/**
 * 채팅 메세지 저장 아웃포트.
 * 한 건의 메세지를 영속화하며, 실제 구현은 infra 레이어의 어댑터가 담당한다.
 */
interface SaveChatMessagePort {

	fun save(message: ChatMessage): ChatMessage
}
