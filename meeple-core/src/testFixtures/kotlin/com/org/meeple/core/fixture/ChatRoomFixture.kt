package com.org.meeple.core.fixture

import com.org.meeple.common.chat.ChatRoomStatus
import com.org.meeple.core.chat.domain.ChatRoom
import java.time.LocalDateTime

/** [ChatRoom] 도메인 모델 테스트 픽스처. 기본은 활성(ACTIVE) 상태의 신규 채팅방이다. */
object ChatRoomFixture {

	fun create(
		id: Long = 0,
		matchId: Long = 1L,
		status: ChatRoomStatus = ChatRoomStatus.ACTIVE,
		expiredAt: LocalDateTime = LocalDateTime.of(2026, 1, 4, 0, 0),
		lastMessage: String? = null,
		lastMessageAt: LocalDateTime? = null,
	): ChatRoom =
		ChatRoom(
			id = id,
			matchId = matchId,
			status = status,
			expiredAt = expiredAt,
			lastMessage = lastMessage,
			lastMessageAt = lastMessageAt,
		)
}
