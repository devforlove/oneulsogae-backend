package com.org.meeple.core.chat.application.port.out

import com.org.meeple.core.chat.domain.ChatRoom

/**
 * 채팅방 저장 아웃포트.
 * 신규 채팅방을 저장하거나, 기존 채팅방(id 존재)의 상태 변경분을 반영한다.
 */
interface SaveChatRoomPort {

	fun save(chatRoom: ChatRoom): ChatRoom
}
