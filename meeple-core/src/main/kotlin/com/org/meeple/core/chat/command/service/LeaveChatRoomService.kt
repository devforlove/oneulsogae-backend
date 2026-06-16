package com.org.meeple.core.chat.command.service

import com.org.meeple.core.chat.ChatErrorCode
import com.org.meeple.core.chat.command.service.port.`in`.LeaveChatRoomUseCase
import com.org.meeple.core.chat.command.service.port.out.GetChatRoomMemberPort
import com.org.meeple.core.chat.command.service.port.out.GetChatRoomPort
import com.org.meeple.core.chat.command.service.port.out.SaveChatRoomMemberPort
import com.org.meeple.core.chat.command.service.port.out.SaveChatRoomPort
import com.org.meeple.core.chat.command.domain.ChatRoom
import com.org.meeple.core.chat.command.domain.ChatRoomMember
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.time.TimeGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [LeaveChatRoomUseCase] 구현.
 * 나가려는 사용자의 참가자([ChatRoomMember]) 행을 로드해(없으면 비참가자로 보고 거절) 소프트 삭제([ChatRoomMember.delete])한다.
 * 채팅방은 **남은 참가자가 없을 때(이 사용자가 마지막 참가자일 때)만** 종료([ChatRoom.close])한다.
 * (그룹챗에서 한 명이 나가도 다른 참가자가 남아 있으면 방은 ACTIVE를 유지하고, 1:1도 같은 규칙으로 마지막 한 명이 나갈 때 닫힌다)
 * 삭제 전 참가자 수로 판정하므로, 수가 1 이하이면 이 사용자가 마지막이다.
 * 삭제된 참가자 행은 @SQLRestriction으로, CLOSED 방은 ACTIVE 필터로 각각 목록·조회에서 제외된다.
 */
@Service
@Transactional
class LeaveChatRoomService(
	private val getChatRoomMemberPort: GetChatRoomMemberPort,
	private val saveChatRoomMemberPort: SaveChatRoomMemberPort,
	private val getChatRoomPort: GetChatRoomPort,
	private val saveChatRoomPort: SaveChatRoomPort,
	private val timeGenerator: TimeGenerator,
) : LeaveChatRoomUseCase {

	override fun leave(userId: Long, chatRoomId: Long) {
		val member: ChatRoomMember = getChatRoomMemberPort.findByChatRoomIdAndUserId(chatRoomId, userId)
			?: throw BusinessException(ChatErrorCode.NOT_CHAT_ROOM_PARTICIPANT)

		// 삭제 전 참가자 수. 1 이하이면 이 사용자가 마지막 참가자다. (삭제 후에는 0명)
		val isLastMember: Boolean = getChatRoomMemberPort.countByChatRoomId(chatRoomId) <= 1L

		val now: LocalDateTime = timeGenerator.now()
		saveChatRoomMemberPort.save(member.delete(now))

		if (isLastMember) {
			val chatRoom: ChatRoom = getChatRoomPort.findById(chatRoomId)
				?: throw BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND)
			saveChatRoomPort.save(chatRoom.close())
		}
	}
}
