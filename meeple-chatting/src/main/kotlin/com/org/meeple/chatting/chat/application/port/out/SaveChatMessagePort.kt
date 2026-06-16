package com.org.meeple.chatting.chat.application.port.out

import com.org.meeple.chatting.chat.domain.ChatMessage

/**
 * 채팅 메세지 저장 아웃포트. (chatting 소유, infra 어댑터가 구현)
 * 한 건의 메세지를 영속화하고 저장된(식별자 채워진) 메세지를 반환한다.
 */
interface SaveChatMessagePort {

	fun save(message: ChatMessage): ChatMessage
}
