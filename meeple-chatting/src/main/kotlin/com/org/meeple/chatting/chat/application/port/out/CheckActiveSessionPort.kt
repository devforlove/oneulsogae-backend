package com.org.meeple.chatting.chat.application.port.out

/**
 * 단일 활성 세션 대조 아웃포트. (chatting 소유, infra 어댑터가 ActiveSessionStore에 위임)
 *
 * HTTP 경로(TokenAuthenticationFilter)가 매 요청에서 활성 세션을 대조하듯, 채팅(STOMP)도
 * CONNECT/SEND 시 토큰의 session_id가 사용자의 현재 활성 세션과 일치하는지 확인해
 * 다른 기기/브라우저의 새 로그인에 밀려난 세션을 차단한다.
 *
 * Redis 장애 시 fail-open(true)은 구현체(ActiveSessionStore)의 정책을 그대로 따른다.
 */
interface CheckActiveSessionPort {

	/** [userId]의 현재 활성 세션이 [sessionId]와 일치하면 true. (밀려났으면 false) */
	fun isActive(userId: Long, sessionId: String): Boolean
}
