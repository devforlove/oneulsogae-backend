package com.org.meeple.core.chat.command.application

import com.org.meeple.core.chat.command.application.port.`in`.DeactivateChatRoomMemberUseCase
import com.org.meeple.core.chat.command.application.port.out.GetChatRoomMemberPort
import com.org.meeple.core.chat.command.application.port.out.GetChatRoomPort
import com.org.meeple.core.chat.command.application.port.out.SaveChatRoomMemberPort
import com.org.meeple.core.chat.command.domain.ChatRoom
import com.org.meeple.core.chat.command.domain.ChatRoomMembers
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [DeactivateChatRoomMemberUseCase] 구현.
 * 매칭 id로 채팅방을 찾아(없으면 no-op) 그 방 참가자 중 [userIds]에 해당하는 행만 비활성([ChatRoomMembers.deactivate])으로 전이해 저장한다.
 * 팀 해체 흐름(같은 트랜잭션)에서 호출되며, 방 자체는 닫지 않는다.
 */
@Service
@Transactional
class DeactivateChatRoomMemberService(
	private val getChatRoomPort: GetChatRoomPort,
	private val getChatRoomMemberPort: GetChatRoomMemberPort,
	private val saveChatRoomMemberPort: SaveChatRoomMemberPort,
) : DeactivateChatRoomMemberUseCase {

	override fun deactivate(matchId: Long, userIds: List<Long>) {
		if (userIds.isEmpty()) return
		val chatRoom: ChatRoom = getChatRoomPort.findByMatchId(matchId) ?: return
		val members: ChatRoomMembers = getChatRoomMemberPort.findAllByChatRoomId(chatRoom.id)
		saveChatRoomMemberPort.saveAll(members.deactivate(userIds.toSet()))
	}
}
