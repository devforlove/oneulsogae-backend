package com.org.meeple.chatting.chat.application.port.`in`.result

/**
 * 읽음 보고 결과(read model). [changed]가 true면 포인터가 실제로 전진한 것이고, false면 변화가 없어(이미 더 앞섬) 브로드캐스트가 불필요하다.
 */
data class MarkMessagesAsReadResult(
	val chatRoomId: Long,
	val readerId: Long,
	val lastReadMessageId: Long,
	val changed: Boolean,
)
