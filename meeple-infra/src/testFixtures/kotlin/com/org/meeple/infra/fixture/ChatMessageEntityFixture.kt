package com.org.meeple.infra.fixture

import com.org.meeple.common.chat.ChatMessageType
import com.org.meeple.infra.chat.command.entity.ChatMessageEntity
import java.time.LocalDateTime

/** [ChatMessageEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다. */
object ChatMessageEntityFixture {

	fun create(
		chatRoomId: Long = 1L,
		senderId: Long? = 1L,
		content: String = "안녕하세요",
		type: ChatMessageType = ChatMessageType.USER,
		sentAt: LocalDateTime = LocalDateTime.now(),
	): ChatMessageEntity =
		ChatMessageEntity(
			chatRoomId = chatRoomId,
			senderId = senderId,
			content = content,
			type = type,
			sentAt = sentAt,
		)
}
