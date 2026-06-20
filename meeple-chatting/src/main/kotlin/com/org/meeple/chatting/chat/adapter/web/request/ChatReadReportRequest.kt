package com.org.meeple.chatting.chat.adapter.web.request

/**
 * 클라이언트가 발행(SEND)하는 읽음 보고 요청. 방을 보고 있는 동안 "여기까지 읽음"을 [lastReadMessageId]로 보고한다.
 * 보고자(readerId)는 인증 Principal에서 서버가 채운다. (클라이언트가 위조할 수 없도록)
 */
data class ChatReadReportRequest(
	val lastReadMessageId: Long,
)
