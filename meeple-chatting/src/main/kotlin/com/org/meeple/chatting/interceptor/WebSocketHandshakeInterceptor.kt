package com.org.meeple.chatting.interceptor

import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

/**
 * WebSocket 핸드셰이크 단계의 인터셉터.
 * 현재는 골격만 제공하며, 인증 토큰 검증 등 핸드셰이크 시점의 처리는 추후 채워 넣는다.
 */
@Component
class WebSocketHandshakeInterceptor : HandshakeInterceptor {
    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        // TODO: 인증/인가 검증 후 통과 여부 반환
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) {
        // TODO: 핸드셰이크 이후 처리
    }
}
