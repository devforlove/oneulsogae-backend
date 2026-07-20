package com.org.oneulsogae.chatting.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionConnectEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import java.util.Collections

// 스프링이 WebSocket 세션을 내부적으로 관리한다.
// 연결/해제 이벤트를 기록해 연결된 세션 수를 확인할 목적. (운영 관찰용 디버그 로그 → prod에서는 기본 비활성)
@Component
class SessionEventListener {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    // 연결/해제 이벤트가 서로 다른 스레드에서 동시에 들어올 수 있어, 동기화된 set으로 동시성 문제를 막는다.
    private val sessions: MutableSet<String?> = Collections.synchronizedSet(mutableSetOf())

    @EventListener
    fun handleSessionConnect(event: SessionConnectEvent) {
        val accessor: StompHeaderAccessor = StompHeaderAccessor.wrap(event.message)

        sessions.add(accessor.sessionId)
        log.debug("WebSocket connected: sessionId={}, currentSessions={}", accessor.sessionId, sessions.size)
    }

    @EventListener
    fun handleSessionDisconnect(event: SessionDisconnectEvent) {
        val accessor: StompHeaderAccessor = StompHeaderAccessor.wrap(event.message)

        sessions.remove(accessor.sessionId)
        log.debug("WebSocket disconnected: sessionId={}, currentSessions={}", accessor.sessionId, sessions.size)
    }
}
