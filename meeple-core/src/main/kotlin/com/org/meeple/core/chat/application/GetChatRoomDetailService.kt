package com.org.meeple.core.chat.application

import com.org.meeple.core.chat.application.port.`in`.GetChatRoomDetailUseCase
import com.org.meeple.core.chat.application.port.out.GetChatMessagePort
import com.org.meeple.core.chat.application.port.out.GetChatParticipantPort
import com.org.meeple.core.chat.application.port.out.GetChatRoomMemberPort
import com.org.meeple.core.chat.application.port.out.GetChatRoomPort
import com.org.meeple.core.chat.domain.ChatMessages
import com.org.meeple.core.chat.domain.ChatParticipants
import com.org.meeple.core.chat.domain.ChatRoom
import com.org.meeple.core.chat.domain.ChatRoomDetail
import com.org.meeple.core.common.error.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetChatRoomDetailUseCase] 구현.
 * 첫 페이지(beforeMessageId == null)에서는 방 상태·참가자들(프로필 포함, 나간 참가자 포함)을 메세지와 함께 내려준다.
 * 이후 커서 페이지에서는 방·참가자 데이터를 다시 싣지 않고(클라이언트가 이미 보유) 더 과거 메세지만 내려준다.
 * 접근 검증은 매 요청마다 한다. 첫 페이지는 어차피 가져온 참가자 목록([GetChatParticipantPort.findByChatRoomId])으로 검증하고,
 * 커서 페이지는 프로필 조인 없이 단건 존재([GetChatRoomMemberPort.existsByChatRoomIdAndUserId])로 가볍게 검증한다. (커서만 바꿔 비참가자가 페이징하는 것을 막는다)
 * 나감(exitedAt) 여부는 DB 기록용일 뿐 노출·검증에서 거르지 않는다. 메세지가 너무 큰 size로 요청돼도 [MAX_MESSAGE_PAGE_SIZE]로 상한을 둔다.
 */
@Service
@Transactional(readOnly = true)
class GetChatRoomDetailService(
	private val getChatRoomPort: GetChatRoomPort,
	private val getChatParticipantPort: GetChatParticipantPort,
	private val getChatRoomMemberPort: GetChatRoomMemberPort,
	private val getChatMessagePort: GetChatMessagePort,
) : GetChatRoomDetailUseCase {

	override fun getChatRoomDetail(userId: Long, chatRoomId: Long, beforeMessageId: Long?, size: Int): ChatRoomDetail {
		val pageSize: Int = size.coerceIn(1, MAX_MESSAGE_PAGE_SIZE)
		return if (beforeMessageId == null) {
			firstPage(userId, chatRoomId, pageSize)
		} else {
			nextPage(userId, chatRoomId, beforeMessageId, pageSize)
		}
	}

	// 첫 페이지: 방 상태·참가자(프로필 포함) 전체 + 최신 메세지. 가져온 참가자 목록으로 접근을 검증한다.
	private fun firstPage(userId: Long, chatRoomId: Long, pageSize: Int): ChatRoomDetail {
		val chatRoom: ChatRoom = getChatRoomPort.findById(chatRoomId)
			?: throw BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND)
		val participants: ChatParticipants = getChatParticipantPort.findByChatRoomId(chatRoomId)
		participants.validateParticipant(userId)

		val messages: ChatMessages = getChatMessagePort.findByChatRoom(chatRoomId, null, pageSize)
		return ChatRoomDetail(
			chatRoomId = chatRoom.id,
			status = chatRoom.status,
			participants = participants.values,
			messages = messages,
		)
	}

	// 이후 커서 페이지: 접근을 단건 존재로 검증하고, 방·참가자 없이 더 과거 메세지만 내려준다.
	private fun nextPage(userId: Long, chatRoomId: Long, beforeMessageId: Long, pageSize: Int): ChatRoomDetail {
		if (!getChatRoomMemberPort.existsByChatRoomIdAndUserId(chatRoomId, userId)) {
			throw BusinessException(ChatErrorCode.NOT_CHAT_ROOM_PARTICIPANT)
		}
		val messages: ChatMessages = getChatMessagePort.findByChatRoom(chatRoomId, beforeMessageId, pageSize)
		return ChatRoomDetail(
			chatRoomId = chatRoomId,
			status = null,
			participants = emptyList(),
			messages = messages,
		)
	}

	companion object {
		/** 한 번에 조회하는 메세지 최대 개수. (과도한 size 요청을 막는 상한) */
		private const val MAX_MESSAGE_PAGE_SIZE: Int = 100
	}
}
