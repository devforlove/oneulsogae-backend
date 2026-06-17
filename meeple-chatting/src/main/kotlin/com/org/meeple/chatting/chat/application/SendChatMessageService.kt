package com.org.meeple.chatting.chat.application

import com.org.meeple.chatting.chat.application.port.`in`.SendChatMessageUseCase
import com.org.meeple.chatting.chat.application.port.`in`.command.SendChatMessageCommand
import com.org.meeple.chatting.chat.application.port.`in`.result.SentChatMessageResult
import com.org.meeple.chatting.chat.application.port.out.GetChatRoomMemberPort
import com.org.meeple.chatting.chat.application.port.out.IncreaseUnreadCountPort
import com.org.meeple.chatting.chat.application.port.out.SaveChatMessagePort
import com.org.meeple.chatting.chat.application.port.out.TimeGenerator
import com.org.meeple.chatting.chat.application.port.out.UpdateChatRoomPort
import com.org.meeple.chatting.chat.domain.ChatMessage
import com.org.meeple.chatting.common.error.ChatException
import com.org.meeple.common.chat.ChatMessageType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [SendChatMessageUseCase] 구현. (chatting 자체 서비스 레이어, core 비의존)
 *
 * 메세지 1건당 DB 접근을 **4쿼리**로 줄였다. (방·참가자를 도메인으로 로드하지 않고 타깃 쿼리로 처리)
 * 1) 발신자 참가 검증 — 단건 존재(exists)
 * 2) 방 종료검증 + 마지막 메세지 갱신 — **조건부 UPDATE 한 번** (활성 방만, 영향 0이면 거부)
 * 3) 메세지 저장 — INSERT
 * 4) 상대방(들) 안 읽은 개수 +1 — **벌크 UPDATE 한 번** (인원수 무관, N+1 제거)
 *
 * 본문 검증(빈 값/길이)은 DB 접근 전에 도메인([ChatMessage.create])에서 먼저 한다.
 */
@Service
@Transactional
class SendChatMessageService(
	private val getChatRoomMemberPort: GetChatRoomMemberPort,
	private val updateChatRoomPort: UpdateChatRoomPort,
	private val saveChatMessagePort: SaveChatMessagePort,
	private val increaseUnreadCountPort: IncreaseUnreadCountPort,
	private val timeGenerator: TimeGenerator,
) : SendChatMessageUseCase {

	override fun send(command: SendChatMessageCommand): SentChatMessageResult {
		val now: LocalDateTime = timeGenerator.now()

		// 본문 검증(빈 값/길이) — DB 접근 없음. SYSTEM이면 보낸이 없이 시스템 메세지로, USER면 발신자를 담아 만든다.
		val message: ChatMessage = when (command.type) {
			ChatMessageType.SYSTEM -> ChatMessage.createSystem(command.chatRoomId, command.content, now)
			ChatMessageType.USER -> ChatMessage.create(command.chatRoomId, command.senderId, command.content, now)
		}

		// 1) 발행자 참가 검증 — SYSTEM 메세지도 그 방의 활성 참가자만 발행할 수 있다. (외부인 위조 차단)
		// (방 last_message를 쓰는 2)보다 먼저 검증해, 비참가자 내용이 방에 써지지 않게 한다)
		if (!getChatRoomMemberPort.existsByChatRoomIdAndUserId(command.chatRoomId, command.senderId)) {
			throw ChatException(ChatErrorCode.NOT_CHAT_ROOM_PARTICIPANT)
		}

		// 2) 활성 방에 한해 마지막 메세지 갱신 (종료 검증을 원자적으로 겸함)
		// 데드락 방지: 이 UPDATE가 chat_rooms 행 X락을 잡는 발송의 첫 락이다. (나가기도 방 락을 먼저 잡음 → 락 순서 일치)
		// 주의: 참가자 안 읽은 개수(chat_room_members) 등 다른 쓰기를 이 UPDATE보다 앞에 두면 락 순서가 엇갈려 데드락이 생길 수 있다.
		if (!updateChatRoomPort.updateLastMessageIfActive(command.chatRoomId, message.content, now)) {
			throw ChatException(ChatErrorCode.CHAT_ROOM_CLOSED)
		}

		// 3) 메세지 저장
		val savedMessage: ChatMessage = saveChatMessagePort.save(message)

		// 4) 상대방(들) 안 읽은 개수 +1
		increaseUnreadCountPort.increaseForOthers(command.chatRoomId, command.senderId)

		return SentChatMessageResult(
			id = savedMessage.id,
			chatRoomId = savedMessage.chatRoomId,
			senderId = savedMessage.senderId,
			content = savedMessage.content,
			type = savedMessage.type,
			sentAt = savedMessage.sentAt,
		)
	}
}
