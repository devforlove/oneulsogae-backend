package com.org.meeple.api.chat.response

import com.org.meeple.common.chat.ChatRoomStatus
import com.org.meeple.core.chat.query.dto.ChatRoomSummary
import java.time.LocalDateTime

/**
 * 채팅방 목록 응답. 조회한 사용자 관점으로 내려준다.
 * 방의 정체성은 참가자에서 파생하므로, 조회 사용자를 제외한 상대 참가자들([participants])을 함께 담는다. (1:1이면 한 명, 그룹챗이면 여러 명)
 * 안 읽은 개수([unreadCount])는 조회 사용자 기준이다.
 */
data class ChatRoomResponse(
	val chatRoomId: Long,
	/** 조회 사용자를 제외한 상대 참가자들. (1:1이면 한 명) */
	val participants: List<ChatParticipantResponse>,
	val status: ChatRoomStatus,
	/** 채팅방 만료 시각. */
	val expiredAt: LocalDateTime,
	/** 조회 사용자가 아직 확인하지 않은 메세지 개수. */
	val unreadCount: Int,
	/** 마지막으로 주고받은 메세지. 아직 없으면 null. */
	val lastMessage: String?,
	/** 마지막 메세지 수신 시각. 아직 없으면 null. */
	val lastMessageAt: LocalDateTime?,
) {
	companion object {
		fun of(summary: ChatRoomSummary): ChatRoomResponse =
			ChatRoomResponse(
				chatRoomId = summary.chatRoomId,
				participants = summary.participants.map { ChatParticipantResponse.of(it) },
				status = summary.status,
				expiredAt = summary.expiredAt,
				unreadCount = summary.unreadCount,
				lastMessage = summary.lastMessage,
				lastMessageAt = summary.lastMessageAt,
			)

		/** 채팅방 요약 목록을 응답 목록으로 변환한다. */
		fun listOf(summaries: List<ChatRoomSummary>): List<ChatRoomResponse> =
			summaries.map { of(it) }
	}
}
