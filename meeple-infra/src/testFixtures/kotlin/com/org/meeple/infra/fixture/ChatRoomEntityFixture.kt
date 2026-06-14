package com.org.meeple.infra.fixture

import com.org.meeple.common.chat.ChatRoomStatus
import com.org.meeple.infra.chat.entity.ChatRoomEntity
import java.time.LocalDateTime

/**
 * [ChatRoomEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * 기본은 ACTIVE 상태이며, 만료 시각은 생성 시점 + 3일, 마지막 메세지 없음이다.
 * 참가자별 안 읽은 개수는 [ChatRoomMemberEntityFixture]로 따로 준비한다.
 */
object ChatRoomEntityFixture {

	fun create(
		matchId: Long = 1L,
		status: ChatRoomStatus = ChatRoomStatus.ACTIVE,
		expiredAt: LocalDateTime = LocalDateTime.now().plusDays(3),
		lastMessage: String? = null,
		lastMessageAt: LocalDateTime? = null,
	): ChatRoomEntity =
		ChatRoomEntity(
			matchId = matchId,
			status = status,
			expiredAt = expiredAt,
			lastMessage = lastMessage,
			lastMessageAt = lastMessageAt,
		)
}
