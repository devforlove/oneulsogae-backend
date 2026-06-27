package com.org.meeple.core.chat.command.application

import com.org.meeple.core.chat.ChatErrorCode
import com.org.meeple.core.chat.command.application.port.`in`.GetChatRoomMatchUseCase
import com.org.meeple.core.chat.command.application.port.`in`.result.ChatRoomMatch
import com.org.meeple.core.chat.command.application.port.out.GetChatRoomPort
import com.org.meeple.core.chat.command.domain.ChatRoom
import com.org.meeple.core.common.error.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetChatRoomMatchUseCase] 구현. (부수효과 없는 조회)
 * 채팅방을 로드해 그 방이 생성된 매칭의 종류+id([ChatRoomMatch])만 추려 돌려준다. 채팅방이 없으면 [ChatErrorCode.CHAT_ROOM_NOT_FOUND].
 */
@Service
class GetChatRoomMatchService(
	private val getChatRoomPort: GetChatRoomPort,
) : GetChatRoomMatchUseCase {

	@Transactional(readOnly = true)
	override fun getMatch(chatRoomId: Long): ChatRoomMatch {
		val chatRoom: ChatRoom = getChatRoomPort.findById(chatRoomId)
			?: throw BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND)
		return ChatRoomMatch(matchType = chatRoom.matchType, matchId = chatRoom.matchId)
	}
}
