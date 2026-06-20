package com.org.meeple.chatting.chat.application

import com.org.meeple.chatting.chat.application.port.`in`.MarkMessagesAsReadUseCase
import com.org.meeple.chatting.chat.application.port.`in`.command.MarkMessagesAsReadCommand
import com.org.meeple.chatting.chat.application.port.`in`.result.MarkMessagesAsReadResult
import com.org.meeple.chatting.chat.application.port.out.AdvanceReadPointerPort
import com.org.meeple.chatting.chat.application.port.out.GetChatRoomMemberPort
import com.org.meeple.chatting.chat.application.port.out.TimeGenerator
import com.org.meeple.chatting.common.error.ChatException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [MarkMessagesAsReadUseCase] 구현. (chatting 자체 서비스, core 비의존)
 *
 * 발송 경로와 동일하게 참가자를 도메인으로 로드하지 않고 타깃 쿼리로 처리한다.
 * 1) 보고자 참가 검증 — 단건 존재(exists). 비참가자면 위조로 보고 거절.
 * 2) 읽음 포인터 forward-only 전진 + 뱃지 리셋 — 조건부 UPDATE 한 번. 갱신 행 0이면 변화 없음(changed=false).
 *
 * 이 경로는 chat_room_members 단일 행만 UPDATE하고 chat_rooms 락을 잡지 않아, 발송/나가기(방 락 선점)와 락 순서 충돌이 없다.
 */
@Service
@Transactional
class MarkMessagesAsReadService(
	private val getChatRoomMemberPort: GetChatRoomMemberPort,
	private val advanceReadPointerPort: AdvanceReadPointerPort,
	private val timeGenerator: TimeGenerator,
) : MarkMessagesAsReadUseCase {

	override fun markAsRead(command: MarkMessagesAsReadCommand): MarkMessagesAsReadResult {
		// 1) 보고자 참가 검증 — 활성 참가자만 읽음을 보고할 수 있다.
		if (!getChatRoomMemberPort.existsByChatRoomIdAndUserId(command.chatRoomId, command.readerId)) {
			throw ChatException(ChatErrorCode.NOT_CHAT_ROOM_PARTICIPANT)
		}

		// 2) 읽음 포인터 forward-only 전진 + 뱃지 리셋. (갱신 행 0이면 이미 더 앞선 포인터 → 변화 없음)
		val now: LocalDateTime = timeGenerator.now()
		val updated: Int = advanceReadPointerPort.advance(command.chatRoomId, command.readerId, command.lastReadMessageId, now)

		return MarkMessagesAsReadResult(
			chatRoomId = command.chatRoomId,
			readerId = command.readerId,
			lastReadMessageId = command.lastReadMessageId,
			changed = updated > 0,
		)
	}
}
