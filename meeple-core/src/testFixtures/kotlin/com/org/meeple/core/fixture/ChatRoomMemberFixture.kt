package com.org.meeple.core.fixture

import com.org.meeple.common.chat.ChatRoomMemberStatus
import com.org.meeple.core.chat.command.domain.ChatRoomMember
import java.time.LocalDateTime

/** [ChatRoomMember] 도메인 모델 테스트 픽스처. 기본은 갓 참가한(읽음 0, 미퇴장) 참가자다. */
object ChatRoomMemberFixture {

	fun create(
		id: Long = 0,
		chatRoomId: Long = 1L,
		userId: Long = 1L,
		status: ChatRoomMemberStatus = ChatRoomMemberStatus.ACTIVE,
		unreadCount: Int = 0,
		lastReadAt: LocalDateTime? = null,
		joinedAt: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0),
		exitedAt: LocalDateTime? = null,
		deletedAt: LocalDateTime? = null,
	): ChatRoomMember =
		ChatRoomMember(
			id = id,
			chatRoomId = chatRoomId,
			userId = userId,
			status = status,
			unreadCount = unreadCount,
			lastReadAt = lastReadAt,
			joinedAt = joinedAt,
			exitedAt = exitedAt,
			deletedAt = deletedAt,
		)
}
