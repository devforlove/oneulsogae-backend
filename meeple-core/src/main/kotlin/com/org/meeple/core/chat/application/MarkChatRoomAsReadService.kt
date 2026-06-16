package com.org.meeple.core.chat.application

import com.org.meeple.core.chat.application.port.`in`.MarkChatRoomAsReadUseCase
import com.org.meeple.core.chat.application.port.out.GetChatRoomMemberPort
import com.org.meeple.core.chat.application.port.out.SaveChatRoomMemberPort
import com.org.meeple.core.chat.domain.ChatRoomMember
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.time.TimeGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [MarkChatRoomAsReadUseCase] 구현.
 * 조회자의 참가자([ChatRoomMember]) 행을 로드해(없으면 비참가자로 보고 거절) 안 읽은 개수를 0으로 되돌리고 마지막 읽음 시각을 갱신한다.
 * 읽음 상태는 참가자 단위로 관리되므로 그 한 행만 갱신하며, 같은 방의 다른 참가자 안 읽은 개수에는 영향이 없다.
 */
@Service
@Transactional
class MarkChatRoomAsReadService(
	private val getChatRoomMemberPort: GetChatRoomMemberPort,
	private val saveChatRoomMemberPort: SaveChatRoomMemberPort,
	private val timeGenerator: TimeGenerator,
) : MarkChatRoomAsReadUseCase {

	override fun markAsRead(userId: Long, chatRoomId: Long) {
		val member: ChatRoomMember = getChatRoomMemberPort.findByChatRoomIdAndUserId(chatRoomId, userId)
			?: throw BusinessException(ChatErrorCode.NOT_CHAT_ROOM_PARTICIPANT)

		val now: LocalDateTime = timeGenerator.now()
		saveChatRoomMemberPort.save(member.markAsRead(now))
	}
}
