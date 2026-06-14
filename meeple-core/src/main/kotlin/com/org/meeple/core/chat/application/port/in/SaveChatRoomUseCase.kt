package com.org.meeple.core.chat.application.port.`in`

import com.org.meeple.core.chat.application.port.`in`.command.SaveChatRoomCommand
import com.org.meeple.core.chat.domain.ChatRoom

/**
 * 채팅방 저장 인포트(유스케이스).
 * 매칭 성사된 남녀 한 쌍([SaveChatRoomCommand])에 대해 새 채팅방(ACTIVE)을 생성·저장하고, 저장된 [ChatRoom]을 반환한다.
 */
interface SaveChatRoomUseCase {

	fun save(command: SaveChatRoomCommand): ChatRoom
}
