package com.org.meeple.core.chat.command.application

import com.org.meeple.common.chat.ChatRoomMatchType
import com.org.meeple.core.chat.command.application.port.`in`.DeactivateChatRoomMemberUseCase
import com.org.meeple.core.chat.command.application.port.out.GetChatRoomMemberPort
import com.org.meeple.core.chat.command.application.port.out.GetChatRoomPort
import com.org.meeple.core.chat.command.application.port.out.SaveChatMessagePort
import com.org.meeple.core.chat.command.application.port.out.SaveChatRoomMemberPort
import com.org.meeple.core.chat.command.application.port.out.SaveChatRoomPort
import com.org.meeple.core.chat.command.domain.ChatMessage
import com.org.meeple.core.chat.command.domain.ChatRoom
import com.org.meeple.core.chat.command.domain.ChatRoomMembers
import com.org.meeple.core.common.time.TimeGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [DeactivateChatRoomMemberUseCase] 구현.
 * 매칭 id로 채팅방을 찾아(없으면 no-op) 그 방 참가자 중 [userIds]에 해당하는 행만 비활성([ChatRoomMembers.deactivate])으로 전이하고,
 * 방에 남는 상대 팀원에게 "상대 팀이 채팅방을 나갔어요" 시스템(SYSTEM) 안내 메세지를 남긴다.
 * (안내 메세지를 저장하고, 방 마지막 메세지와 남는 참가자의 안 읽은 개수를 갱신한다. 실시간 브로드캐스트는 하지 않는다)
 * 팀 해체 흐름(같은 트랜잭션)에서 호출되며, 방 자체는 닫지 않는다. 시각은 [TimeGenerator]로 얻는다.
 */
@Service
@Transactional
class DeactivateChatRoomMemberService(
	private val getChatRoomPort: GetChatRoomPort,
	private val getChatRoomMemberPort: GetChatRoomMemberPort,
	private val saveChatRoomMemberPort: SaveChatRoomMemberPort,
	private val saveChatRoomPort: SaveChatRoomPort,
	private val saveChatMessagePort: SaveChatMessagePort,
	private val timeGenerator: TimeGenerator,
) : DeactivateChatRoomMemberUseCase {

	override fun deactivate(matchType: ChatRoomMatchType, matchId: Long, userIds: List<Long>) {
		if (userIds.isEmpty()) return
		val chatRoom: ChatRoom = getChatRoomPort.findByMatchTypeAndMatchId(matchType, matchId) ?: return
		val leaving: Set<Long> = userIds.toSet()
		val members: ChatRoomMembers = getChatRoomMemberPort.findAllByChatRoomId(chatRoom.id)
		val now: LocalDateTime = timeGenerator.now()

		// 나가는 팀원 비활성화 → 채팅방 입장 차단
		saveChatRoomMemberPort.saveAll(members.deactivate(leaving))

		// "상대 팀이 채팅방을 나갔어요" 시스템 안내 메세지 저장 + 방 마지막 메세지·남는 참가자 안 읽음 갱신
		val message: ChatMessage = ChatMessage.createSystem(chatRoom.id, TEAM_LEFT_MESSAGE, now)
		saveChatMessagePort.save(message)
		saveChatRoomPort.save(chatRoom.receiveMessage(message.content, now))
		saveChatRoomMemberPort.saveAll(members.receiveExcept(leaving))
	}

	companion object {

		/** 팀 해체로 한 팀이 채팅방을 떠났음을 남는 상대 팀원에게 알리는 안내 문구. */
		private const val TEAM_LEFT_MESSAGE: String = "상대 팀이 채팅방을 나갔어요"
	}
}
