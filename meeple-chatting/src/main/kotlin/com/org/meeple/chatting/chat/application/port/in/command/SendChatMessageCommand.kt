package com.org.meeple.chatting.chat.application.port.`in`.command

/**
 * 채팅 메세지 발송 입력.
 * 어느 방([chatRoomId])에 누가([senderId]) 무슨 본문([content])을 보내는지 받는다.
 * 보낸 시각/식별자는 도메인·서버가 채운다. ([senderId]는 컨트롤러가 인증 Principal에서 채운다)
 */
data class SendChatMessageCommand(
	val chatRoomId: Long,
	val senderId: Long,
	val content: String,
)
