package com.org.oneulsogae.chatting.chat.adapter.web.response

/**
 * 개인 큐(`/user/queue/errors`)로 통지하는 채팅 에러 응답.
 * 구독 거부처럼 연결은 유지하되 클라이언트에 사유를 알릴 때 쓴다.
 * [destination]은 거부된 목적지(어떤 구독/발행이 거부됐는지)다.
 */
data class ChatErrorResponse(
	val code: String,
	val message: String,
	val destination: String?,
)
