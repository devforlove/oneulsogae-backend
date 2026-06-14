package com.org.meeple.chatting.config

import com.org.meeple.chatting.interceptor.StompAuthChannelInterceptor
import com.org.meeple.chatting.interceptor.WebSocketHandshakeInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

/**
 * STOMP over WebSocket 설정.
 *
 * - 클라이언트는 `/ws/chat`으로 핸드셰이크한 뒤, 그 위에서 STOMP 프레임으로 통신한다.
 *   (핸드셰이크는 HTTP Upgrade 요청이라 기존 Security 필터의 인증을 그대로 받는다)
 * - 발행(SEND) 목적지 prefix `/app`은 [com.org.meeple.chatting.controller.ChatMessageController]의 `@MessageMapping`으로 라우팅된다.
 * - 구독(SUBSCRIBE) 목적지: `/topic`(방 단위 브로드캐스트), `/queue`(개인 수신).
 * - 사용자별 목적지 prefix는 `/user` (convertAndSendToUser → user 큐).
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
	private val webSocketHandshakeInterceptor: WebSocketHandshakeInterceptor,
	private val stompAuthChannelInterceptor: StompAuthChannelInterceptor,
) : WebSocketMessageBrokerConfigurer {

	override fun registerStompEndpoints(registry: StompEndpointRegistry) {
		registry.addEndpoint("/ws/chat")
			.addInterceptors(webSocketHandshakeInterceptor)
			.setAllowedOriginPatterns("*") // production이라면 도메인 고려
		// 브라우저 폴백이 필요하면 위 체인 끝에 .withSockJS() 를 추가한다.
	}

	override fun configureMessageBroker(registry: MessageBrokerRegistry) {
		// 내장 simple broker(인메모리). 다중 인스턴스로 확장 시 외부 브로커(RabbitMQ STOMP relay)나 Redis 전파로 교체해야 한다.
		registry.enableSimpleBroker("/topic", "/queue")
		registry.setApplicationDestinationPrefixes("/app")
		registry.setUserDestinationPrefix("/user")
	}

	override fun configureClientInboundChannel(registration: ChannelRegistration) {
		// CONNECT 등 STOMP 프레임 단계의 인증/처리는 인바운드 채널 인터셉터에서 수행한다.
		registration.interceptors(stompAuthChannelInterceptor)
	}
}
