package com.org.oneulsogae.core.fixture

import com.org.oneulsogae.core.chat.command.domain.ChatMessage
import java.time.LocalDateTime

/** [ChatMessage] 도메인 모델 테스트 픽스처. */
object ChatMessageFixture {

	fun create(
		id: Long = 0,
		chatRoomId: Long = 1L,
		senderId: Long = 1L,
		content: String = "안녕하세요",
		sentAt: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0),
	): ChatMessage =
		ChatMessage(
			id = id,
			chatRoomId = chatRoomId,
			senderId = senderId,
			content = content,
			sentAt = sentAt,
		)
}
