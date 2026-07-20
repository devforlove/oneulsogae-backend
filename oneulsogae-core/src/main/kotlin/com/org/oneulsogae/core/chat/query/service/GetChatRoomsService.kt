package com.org.oneulsogae.core.chat.query.service

import com.org.oneulsogae.core.chat.query.service.port.`in`.GetChatRoomsUseCase
import com.org.oneulsogae.core.chat.query.dto.ChatRoomSummary
import com.org.oneulsogae.core.chat.query.dao.GetChatRoomDao
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetChatRoomsUseCase] 구현.
 * 사용자가 참가한 ACTIVE 채팅방 목록을 상대방 닉네임·프로필 이미지, 사용자 관점의 안 읽은 개수와 함께 조회 리포지토리에 위임해 반환한다.
 * 참가/읽음 상태가 참가자 단위([com.org.oneulsogae.core.chat.command.domain.ChatRoomMember])로 관리되므로, 성별을 따지지 않고 user_id로 방을 찾는다.
 */
@Service
@Transactional(readOnly = true)
class GetChatRoomsService(
	private val getChatRoomDao: GetChatRoomDao,
) : GetChatRoomsUseCase {

	override fun getActiveChatRooms(userId: Long): List<ChatRoomSummary> =
		getChatRoomDao.findActiveByUserId(userId)
}
