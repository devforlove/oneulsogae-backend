package com.org.meeple.chatting.config

import org.springframework.web.socket.WebSocketSession

/**
 * 사용자별 활성 WebSocket 물리 소켓을 추적하고, 새 로그인에 밀려난 이전 연결을 끊는다.
 *
 * 단일 활성 세션은 [com.org.meeple.chatting.chat.application.port.out.CheckActiveSessionPort](Redis 마커)로
 * "연결 시도"를 막지만, 이미 열려 있는 소켓은 막지 못한다. 이 레지스트리가 그 빈틈을 메워, CONNECT 시점에
 * 같은 사용자의 이전 연결(다른 JWT session_id)을 물리적으로 닫아 단일 세션을 실제로 강제한다.
 *
 * 등록/해제는 핸드셰이크 수명주기([com.org.meeple.chatting.config.SessionRegisteringHandlerDecorator])가,
 * 사용자 바인딩/축출은 CONNECT 인증([AuthChannelInterceptor])이 호출한다.
 */
interface WebSocketSessionRegistry {

	/** 핸드셰이크 직후 호출. 아직 인증 전이라 소켓만 wsSessionId로 등록해 둔다. */
	fun register(session: WebSocketSession)

	/** 연결 종료 시 호출. 추적 정보를 정리한다. (강제 종료로 인한 종료 콜백도 여기로 들어온다) */
	fun unregister(wsSessionId: String)

	/**
	 * CONNECT 인증 후 호출. [wsSessionId] 연결을 ([userId], [jwtSessionId])로 바인딩하고,
	 * 같은 [userId]의 **다른 JWT session_id** 연결을 모두 닫는다. (같은 로그인의 여러 탭은 유지)
	 */
	fun bindAndEvictPrevious(userId: Long, jwtSessionId: String, wsSessionId: String)
}
