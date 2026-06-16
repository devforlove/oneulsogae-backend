package com.org.meeple.chatting.common.error

import com.org.meeple.chatting.chat.application.ChatErrorCode

/**
 * 채팅(WebSocket/STOMP) 도메인 예외.
 * chatting 모듈 자체 예외로, [ChatErrorCode]를 담아 던진다. (인터셉터/메세지 핸들러에서 던지면 STOMP ERROR 프레임으로 전달된다)
 */
class ChatException(
	val errorCode: ChatErrorCode,
) : RuntimeException(errorCode.message)
