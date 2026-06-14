package com.org.meeple.core.chat.application.port.`in`

import com.org.meeple.core.chat.domain.ChatRoomSummary

/**
 * 채팅방 목록 조회 인포트(유스케이스).
 * 사용자에게 할당된 ACTIVE 상태의 채팅방 목록을 상대방 닉네임·프로필 이미지와 함께 반환한다.
 */
interface GetChatRoomsUseCase {

	fun getActiveChatRooms(userId: Long): List<ChatRoomSummary>
}
