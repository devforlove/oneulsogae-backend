package com.org.meeple.core.chat.application

import com.org.meeple.core.chat.application.port.`in`.SaveChatRoomUseCase
import com.org.meeple.core.chat.application.port.`in`.command.SaveChatRoomCommand
import com.org.meeple.core.chat.application.port.out.GetChatRoomPort
import com.org.meeple.core.chat.application.port.out.SaveChatRoomMemberPort
import com.org.meeple.core.chat.application.port.out.SaveChatRoomPort
import com.org.meeple.core.chat.domain.ChatRoom
import com.org.meeple.core.chat.domain.ChatRoomMember
import com.org.meeple.core.chat.domain.ChatRoomMembers
import com.org.meeple.core.common.time.TimeGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [SaveChatRoomUseCase] 구현.
 * 새 채팅방을 ACTIVE 상태로 생성·저장하고, 참가자([ChatRoomMember])들을 같은 트랜잭션에서 함께 생성한다.
 * 만료 시각은 생성 시각([TimeGenerator.now]) + [ChatRoom.EXPIRATION]으로 도메인이 정한다.
 * 참가자별 안 읽은 개수·읽음 시각은 멤버가 보관하므로, 방 저장으로 얻은 id로 참가자 행들을 만든다. (1:1이면 두 명, 그룹챗이면 여러 명)
 */
@Service
@Transactional
class SaveChatRoomService(
	private val getChatRoomPort: GetChatRoomPort,
	private val saveChatRoomPort: SaveChatRoomPort,
	private val saveChatRoomMemberPort: SaveChatRoomMemberPort,
	private val timeGenerator: TimeGenerator,
) : SaveChatRoomUseCase {

	override fun save(command: SaveChatRoomCommand): ChatRoom {
		// 멱등 생성: 이미 이 매칭의 채팅방이 있으면 새로 만들지 않고 그대로 반환한다. (이벤트 재전달/중복 호출 대비)
		getChatRoomPort.findByMatchId(command.matchId)?.let { return it }

		val now: LocalDateTime = timeGenerator.now()
		val chatRoom: ChatRoom = saveChatRoomPort.save(
			ChatRoom.open(matchId = command.matchId, now = now),
		)
		saveChatRoomMemberPort.saveAll(
			ChatRoomMembers(
				command.participantUserIds.map { participantUserId: Long ->
					ChatRoomMember.join(chatRoomId = chatRoom.id, userId = participantUserId, now = now)
				},
			),
		)
		return chatRoom
	}
}
