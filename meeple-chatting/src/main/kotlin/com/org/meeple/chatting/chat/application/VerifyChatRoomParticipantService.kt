package com.org.meeple.chatting.chat.application

import com.org.meeple.chatting.chat.application.port.`in`.VerifyChatRoomParticipantUseCase
import com.org.meeple.chatting.chat.application.port.out.GetChatRoomMemberPort
import com.org.meeple.chatting.common.error.ChatException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [VerifyChatRoomParticipantUseCase] 구현. (chatting 자체 서비스 레이어, core 비의존)
 * 구독 인가는 프로필 표시가 필요 없는 접근 검증이므로 단건 존재로만 가볍게 확인한다.
 */
@Service
@Transactional(readOnly = true)
class VerifyChatRoomParticipantService(
	private val getChatRoomMemberPort: GetChatRoomMemberPort,
) : VerifyChatRoomParticipantUseCase {

	override fun verifyParticipant(userId: Long, chatRoomId: Long) {
		if (!getChatRoomMemberPort.existsByChatRoomIdAndUserId(chatRoomId, userId)) {
			throw ChatException(ChatErrorCode.NOT_CHAT_ROOM_PARTICIPANT)
		}
	}
}
