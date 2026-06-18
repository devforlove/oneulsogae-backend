package com.org.meeple.chatting.config

import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.WebSocketHandlerDecorator

/**
 * WebSocket 핸드셰이크 수명주기에 맞춰 물리 소켓을 [WebSocketSessionRegistry]에 등록/해제하는 데코레이터.
 *
 * 등록은 핸드셰이크 직후(아직 STOMP CONNECT 전, 인증 전)에 일어나므로 wsSessionId만 보관한다.
 * 사용자 바인딩은 이후 CONNECT 프레임에서 [AuthChannelInterceptor]가 한다.
 * 새 로그인에 밀려나 강제 종료될 때도 이 종료 콜백을 거쳐 추적 정보가 정리된다.
 */
class SessionRegisteringHandlerDecorator(
	delegate: WebSocketHandler,
	private val registry: WebSocketSessionRegistry,
) : WebSocketHandlerDecorator(delegate) {

	override fun afterConnectionEstablished(session: WebSocketSession) {
		registry.register(session)
		super.afterConnectionEstablished(session)
	}

	override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
		try {
			super.afterConnectionClosed(session, closeStatus)
		} finally {
			registry.unregister(session.id)
		}
	}
}
