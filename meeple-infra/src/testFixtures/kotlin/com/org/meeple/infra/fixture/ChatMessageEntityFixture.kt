package com.org.meeple.infra.fixture

import com.org.meeple.infra.chat.entity.ChatMessageEntity
import java.time.LocalDateTime

/** [ChatMessageEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다. */
object ChatMessageEntityFixture {

	fun create(
		chatRoomId: Long = 1L,
		senderId: Long = 1L,
		content: String = "안녕하세요",
		sentAt: LocalDateTime = LocalDateTime.now(),
	): ChatMessageEntity =
		ChatMessageEntity(
			chatRoomId = chatRoomId,
			senderId = senderId,
			content = content,
			sentAt = sentAt,
		)
}
