package com.org.oneulsogae.core.fixture

import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.common.chat.ChatRoomStatus
import com.org.oneulsogae.core.chat.command.domain.ChatRoom
import java.time.LocalDateTime

/** [ChatRoom] 도메인 모델 테스트 픽스처. 기본은 1:1(solo) 매칭에서 만들어진 활성(ACTIVE) 상태의 신규 채팅방이다. */
object ChatRoomFixture {

	fun create(
		id: Long = 0,
		matchType: ChatRoomMatchType = ChatRoomMatchType.SOLO,
		matchId: Long = 1L,
		status: ChatRoomStatus = ChatRoomStatus.ACTIVE,
		expiredAt: LocalDateTime = LocalDateTime.of(2026, 1, 4, 0, 0),
		lastMessage: String? = null,
		lastMessageAt: LocalDateTime? = null,
	): ChatRoom =
		ChatRoom(
			id = id,
			matchType = matchType,
			matchId = matchId,
			status = status,
			expiredAt = expiredAt,
			lastMessage = lastMessage,
			lastMessageAt = lastMessageAt,
		)
}
