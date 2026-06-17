package com.org.meeple.chatting.chat.adapter.web.request

import com.org.meeple.common.chat.ChatMessageType

/**
 * 클라이언트가 발행(SEND)하는 채팅 메세지 요청. 본문([message])과 유형([type])을 받는다.
 * 보낸이(senderId)는 인증 Principal에서, 보낸 시각·식별자(messageId)는 서버가 채운다. (클라이언트가 위조할 수 없도록)
 * [type]은 일반 메세지면 생략(USER 기본)하고, "OO님이 나갔어요" 같은 시스템 안내는 SYSTEM으로 발행한다. (보낸이 없이 시스템 메세지로 저장·렌더)
 * 브로드캐스트(server→구독자) 표현은 [ChatMessageDto]가 따로 담당한다.
 */
data class ChatMessageSendRequest(
	val message: String,
	val type: ChatMessageType = ChatMessageType.USER,
)
