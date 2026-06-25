package com.org.meeple.infra.fixture

import com.org.meeple.common.chat.ChatRoomMemberStatus
import com.org.meeple.infra.chat.command.entity.ChatRoomMemberEntity
import java.time.LocalDateTime

/**
 * [ChatRoomMemberEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * 기본은 갓 참가한(안 읽은 개수 0, 마지막 읽음 없음, 미퇴장) 참가자다.
 */
object ChatRoomMemberEntityFixture {

	fun create(
		chatRoomId: Long = 1L,
		userId: Long = 1L,
		teamId: Long? = null,
		status: ChatRoomMemberStatus = ChatRoomMemberStatus.ACTIVE,
		unreadCount: Int = 0,
		lastReadAt: LocalDateTime? = null,
		lastReadMessageId: Long? = null,
		joinedAt: LocalDateTime = LocalDateTime.now(),
		exitedAt: LocalDateTime? = null,
	): ChatRoomMemberEntity =
		ChatRoomMemberEntity(
			chatRoomId = chatRoomId,
			userId = userId,
			teamId = teamId,
			status = status,
			unreadCount = unreadCount,
			lastReadAt = lastReadAt,
			lastReadMessageId = lastReadMessageId,
			joinedAt = joinedAt,
			exitedAt = exitedAt,
		)
}
