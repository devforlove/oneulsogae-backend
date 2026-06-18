package com.org.meeple.chatting.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * [WebSocketSessionRegistry]의 인메모리 구현.
 *
 * 단일 인스턴스(내장 simple broker) 기준이다. 다중 인스턴스로 확장하면 다른 인스턴스에 붙은 소켓은
 * 닫지 못하므로, 외부 브로커 전환 시 사용자→인스턴스 라우팅이나 브로커 차원의 축출로 보강해야 한다.
 */
@Component
class InMemoryWebSocketSessionRegistry : WebSocketSessionRegistry {

	/** wsSessionId -> 물리 소켓. (핸드셰이크 시점부터 보관) */
	private val sessions: ConcurrentHashMap<String, WebSocketSession> = ConcurrentHashMap()

	/** wsSessionId -> 인증 정보. CONNECT 인증 후에 채워진다. */
	private val connections: ConcurrentHashMap<String, Connection> = ConcurrentHashMap()

	/** userId -> 그 사용자의 wsSessionId 집합. */
	private val userSessions: ConcurrentHashMap<Long, MutableSet<String>> = ConcurrentHashMap()

	override fun register(session: WebSocketSession) {
		sessions[session.id] = session
	}

	override fun unregister(wsSessionId: String) {
		sessions.remove(wsSessionId)
		val connection: Connection = connections.remove(wsSessionId) ?: return
		userSessions.computeIfPresent(connection.userId) { _: Long, ids: MutableSet<String> ->
			ids.remove(wsSessionId)
			if (ids.isEmpty()) null else ids
		}
	}

	override fun bindAndEvictPrevious(userId: Long, jwtSessionId: String, wsSessionId: String) {
		connections[wsSessionId] = Connection(userId, jwtSessionId)
		val ids: MutableSet<String> =
			userSessions.computeIfAbsent(userId) { _: Long -> Collections.synchronizedSet(HashSet()) }

		// 닫을 대상을 잠금 안에서 수집하고, 실제 close()는 잠금 밖에서 호출한다.
		// (close가 동기적으로 종료 콜백→unregister를 유발해 같은 맵을 건드릴 수 있어 재진입을 피한다)
		val toClose: List<String>
		synchronized(ids) {
			ids.add(wsSessionId)
			toClose = ids.filter { id: String ->
				if (id == wsSessionId) return@filter false
				val other: Connection = connections[id] ?: return@filter false
				other.jwtSessionId != jwtSessionId
			}
		}
		toClose.forEach { id: String -> closeAsSuperseded(id) }
	}

	// 밀려난 소켓을 닫는다. 종료는 afterConnectionClosed→unregister로 정리되므로 여기선 close만 한다.
	private fun closeAsSuperseded(wsSessionId: String) {
		val session: WebSocketSession = sessions[wsSessionId] ?: return
		try {
			session.close(SESSION_TAKEN_OVER)
		} catch (e: Exception) {
			// 정리 경로다. 이미 닫히는 중이거나 I/O 오류여도 전파하지 않고 로그만 남긴다.
			log.warn("밀려난 WebSocket 종료 실패 wsSessionId={}", wsSessionId, e)
		}
	}

	/** 한 WebSocket 연결의 인증 정보. */
	private data class Connection(val userId: Long, val jwtSessionId: String)

	companion object {
		private val log: Logger = LoggerFactory.getLogger(InMemoryWebSocketSessionRegistry::class.java)

		// 4000~4999는 애플리케이션 정의 close code. 새 로그인에 밀려나 닫혔음을 클라이언트가 식별하게 한다.
		private val SESSION_TAKEN_OVER: CloseStatus = CloseStatus(4001, "session-taken-over")
	}
}
