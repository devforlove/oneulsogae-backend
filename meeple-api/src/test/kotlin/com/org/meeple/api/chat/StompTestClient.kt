package com.org.meeple.api.chat

import org.springframework.messaging.converter.JacksonJsonMessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import org.springframework.web.socket.sockjs.client.SockJsClient
import org.springframework.web.socket.sockjs.client.WebSocketTransport
import java.lang.reflect.Type
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * STOMP-over-SockJS 테스트 클라이언트. (`/ws/chat`이 withSockJS()로 등록돼 있어 SockJsClient로 접속한다)
 * CONNECT 시 Authorization 헤더로 인증하고, SUBSCRIBE 수신을 큐로 모아 테스트에서 await 한다.
 */
class StompTestClient(port: Int, accessToken: String) {

	private val client: WebSocketStompClient = WebSocketStompClient(
		SockJsClient(listOf(WebSocketTransport(StandardWebSocketClient()))),
	).apply {
		messageConverter = JacksonJsonMessageConverter()
	}

	private val session: StompSession

	init {
		val connectHeaders: StompHeaders = StompHeaders().apply {
			add("Authorization", "Bearer $accessToken")
		}
		session = client.connectAsync(
			"http://localhost:$port/ws/chat",
			WebSocketHttpHeaders(),
			connectHeaders,
			object : StompSessionHandlerAdapter() {},
		).get(5, TimeUnit.SECONDS)
	}

	/** [destination]을 구독하고, 수신 페이로드를 [type]으로 역직렬화해 큐에 모은다. */
	fun <T : Any> subscribe(destination: String, type: Class<T>): BlockingQueue<T> {
		val queue: BlockingQueue<T> = LinkedBlockingQueue()
		session.subscribe(
			destination,
			object : StompFrameHandler {
				override fun getPayloadType(headers: StompHeaders): Type = type

				override fun handleFrame(headers: StompHeaders, payload: Any?) {
					if (payload != null) {
						@Suppress("UNCHECKED_CAST")
						queue.add(payload as T)
					}
				}
			},
		)
		return queue
	}

	/** [destination]으로 [payload]를 발행한다. */
	fun send(destination: String, payload: Any) {
		session.send(destination, payload)
	}

	fun disconnect() {
		if (session.isConnected) {
			session.disconnect()
		}
	}
}
