package com.org.meeple.core.chat.domain

import com.org.meeple.common.chat.ChatRoomStatus
import java.time.LocalDateTime

/**
 * 채팅방 목록 조회 결과(read model / DTO). 조회 사용자 관점으로 내려준다.
 * 방의 정체성은 참가자에서 매번 파생하므로, 조회 사용자를 제외한 상대 참가자들([participants])을 함께 담는다.
 * (1:1 채팅이면 한 명, 그룹챗이면 여러 명) [unreadCount]는 조회 사용자가 안 읽은 개수다. (조회 사용자의 참가자(ChatRoomMember) 행에서 가져온다)
 * 참가자 식별·프로필은 [ChatParticipant]를 재사용한다.
 */
data class ChatRoomSummary(
	val chatRoomId: Long,
	val participants: List<ChatParticipant>,
	val status: ChatRoomStatus,
	val expiredAt: LocalDateTime,
	val unreadCount: Int,
	val lastMessage: String?,
	val lastMessageAt: LocalDateTime?,
)
