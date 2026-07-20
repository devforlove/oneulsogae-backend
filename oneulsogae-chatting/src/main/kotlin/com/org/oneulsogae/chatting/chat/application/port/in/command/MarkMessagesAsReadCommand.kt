package com.org.oneulsogae.chatting.chat.application.port.`in`.command

/**
 * 읽음 보고 커맨드. [readerId]가 [chatRoomId] 방에서 [lastReadMessageId]까지 읽었음을 보고한다.
 * (읽음 포인터는 forward-only로 전진하므로 현재 포인터보다 작은 값은 무시된다)
 */
data class MarkMessagesAsReadCommand(
	val chatRoomId: Long,
	val readerId: Long,
	val lastReadMessageId: Long,
)
