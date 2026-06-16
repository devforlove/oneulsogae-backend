package com.org.meeple.chatting.chat.adapter.web.request

/**
 * 클라이언트가 발행(SEND)하는 채팅 메세지 요청. 본문([message])만 받는다.
 * 보낸이(senderId)는 인증 Principal에서, 보낸 시각·식별자(messageId)는 서버가 채운다. (클라이언트가 위조할 수 없도록)
 * 브로드캐스트(server→구독자) 표현은 [ChatMessageDto]가 따로 담당한다.
 */
data class ChatMessageSendRequest(
	val message: String,
)
