package com.org.oneulsogae.core.chat.query.service

import com.org.oneulsogae.core.chat.ChatErrorCode
import com.org.oneulsogae.core.chat.query.service.port.`in`.GetChatRoomDetailUseCase
import com.org.oneulsogae.core.chat.query.dto.ChatMessageViews
import com.org.oneulsogae.core.chat.query.dto.ChatParticipants
import com.org.oneulsogae.core.chat.query.dto.ChatRoomDetail
import com.org.oneulsogae.core.chat.query.dto.ChatRoomView
import com.org.oneulsogae.core.chat.query.dao.GetChatMessageDao
import com.org.oneulsogae.core.chat.query.dao.GetChatParticipantDao
import com.org.oneulsogae.core.chat.query.dao.ExistsChatRoomMemberDao
import com.org.oneulsogae.core.chat.query.dao.GetChatRoomDao
import com.org.oneulsogae.core.common.error.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetChatRoomDetailUseCase] 구현.
 * 첫 페이지(beforeMessageId == null)에서는 방 상태·활성 참가자들(프로필 포함)을 메세지와 함께 내려준다.
 * 이후 커서 페이지에서는 방·참가자 데이터를 다시 싣지 않고(클라이언트가 이미 보유) 더 과거 메세지만 내려준다.
 * 접근 검증은 매 요청마다 한다. 첫 페이지는 어차피 가져온 활성 참가자 목록([GetChatParticipantDao.findByChatRoomId])으로 검증하고,
 * 커서 페이지는 프로필 조인 없이 단건 존재([ExistsChatRoomMemberDao.existsByChatRoomIdAndUserId])로 가볍게 검증한다. (커서만 바꿔 비참가자가 페이징하는 것을 막는다)
 * 나간(DEACTIVE) 참가자는 비참가자로 취급해 노출·검증에서 모두 제외된다. 메세지가 너무 큰 size로 요청돼도 [MAX_MESSAGE_PAGE_SIZE]로 상한을 둔다.
 */
@Service
@Transactional(readOnly = true)
class GetChatRoomDetailService(
	private val getChatRoomDao: GetChatRoomDao,
	private val getChatParticipantDao: GetChatParticipantDao,
	private val existsChatRoomMemberDao: ExistsChatRoomMemberDao,
	private val getChatMessageDao: GetChatMessageDao,
) : GetChatRoomDetailUseCase {

	override fun getChatRoomDetail(userId: Long, chatRoomId: Long, beforeMessageId: Long?, size: Int): ChatRoomDetail {
		val pageSize: Int = size.coerceIn(1, MAX_MESSAGE_PAGE_SIZE)
		return if (beforeMessageId == null) {
			firstPage(userId, chatRoomId, pageSize)
		} else {
			nextPage(userId, chatRoomId, beforeMessageId, pageSize)
		}
	}

	// 첫 페이지: 방 상태·참가자(프로필 포함) 전체 + 최신 메세지. 접근은 활성 참가자 존재로 검증한다.
	// (참가자 목록은 프로필 노출을 위해 나간(DEACTIVE) 사용자도 포함하므로, 그 목록으로 접근을 검증하면 나간 사용자도 통과한다. 그래서 활성 존재 확인으로 분리한다)
	private fun firstPage(userId: Long, chatRoomId: Long, pageSize: Int): ChatRoomDetail {
		val chatRoom: ChatRoomView = getChatRoomDao.findById(chatRoomId)
			?: throw BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND)
		if (!existsChatRoomMemberDao.existsByChatRoomIdAndUserId(chatRoomId, userId)) {
			throw BusinessException(ChatErrorCode.NOT_CHAT_ROOM_PARTICIPANT)
		}
		val participants: ChatParticipants = getChatParticipantDao.findByChatRoomId(chatRoomId, userId)

		val messages: ChatMessageViews = getChatMessageDao.findByChatRoom(chatRoomId, null, pageSize)
		return ChatRoomDetail(
			chatRoomId = chatRoom.chatRoomId,
			matchType = chatRoom.matchType,
			status = chatRoom.status,
			participants = participants.values,
			messages = messages,
		)
	}

	// 이후 커서 페이지: 접근을 단건 존재로 검증하고, 방·참가자 없이 더 과거 메세지만 내려준다.
	private fun nextPage(userId: Long, chatRoomId: Long, beforeMessageId: Long, pageSize: Int): ChatRoomDetail {
		if (!existsChatRoomMemberDao.existsByChatRoomIdAndUserId(chatRoomId, userId)) {
			throw BusinessException(ChatErrorCode.NOT_CHAT_ROOM_PARTICIPANT)
		}
		val messages: ChatMessageViews = getChatMessageDao.findByChatRoom(chatRoomId, beforeMessageId, pageSize)
		return ChatRoomDetail(
			chatRoomId = chatRoomId,
			matchType = null,
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
