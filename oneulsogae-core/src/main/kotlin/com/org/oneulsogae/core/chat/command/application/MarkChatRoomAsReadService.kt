package com.org.oneulsogae.core.chat.command.application

import com.org.oneulsogae.core.chat.ChatErrorCode
import com.org.oneulsogae.core.chat.command.application.port.`in`.MarkChatRoomAsReadUseCase
import com.org.oneulsogae.core.chat.command.application.port.out.SaveChatRoomMemberPort
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.time.TimeGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [MarkChatRoomAsReadUseCase] 구현.
 * 조회자의 안 읽은 개수(뱃지)를 0으로 되돌리고 마지막 읽음 시각을 갱신한다. 읽음 상태는 참가자 단위라 본인 행만 갱신된다.
 * 엔티티 전체 머지(blind overwrite) 대신 타깃 UPDATE를 쓴다: WS 읽음 경로가 전진시킨 읽음 포인터(lastReadMessageId)를
 * 옛 값으로 덮어 역행시키거나(읽음영수증 회귀), 동시 증가분을 잃는 lost update를 막기 위함이다. (읽음 포인터는 WS 경로가 전담)
 */
@Service
@Transactional
class MarkChatRoomAsReadService(
	private val saveChatRoomMemberPort: SaveChatRoomMemberPort,
	private val timeGenerator: TimeGenerator,
) : MarkChatRoomAsReadUseCase {

	override fun markAsRead(userId: Long, chatRoomId: Long) {
		val now: LocalDateTime = timeGenerator.now()
		// 안 읽은 개수만 타깃 UPDATE로 0으로 되돌린다. 갱신 행이 0이면 (소프트삭제 안 된) 참가자가 없는 것이므로 비참가자로 보고 거절한다.
		val updated: Int = saveChatRoomMemberPort.resetUnreadCount(chatRoomId, userId, now)
		if (updated == 0) {
			throw BusinessException(ChatErrorCode.NOT_CHAT_ROOM_PARTICIPANT)
		}
	}
}
