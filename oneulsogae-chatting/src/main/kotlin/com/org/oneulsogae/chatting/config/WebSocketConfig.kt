package com.org.oneulsogae.chatting.config

import org.springframework.beans.factory.annotation.Value
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
 *   (핸드셰이크 경로는 SecurityConfig에서 permitAll로 열고, 인증은 CONNECT 프레임에서 [AuthChannelInterceptor]가 한다)
 * - 핸드셰이크 허용 오리진은 앱 CORS 설정(`app.cors.allowed-origins`)과 동일한 프론트 오리진으로 제한한다. (와일드카드 금지)
 * - 발행(SEND) 목적지 prefix `/app`은 [com.org.oneulsogae.chatting.chat.adapter.web.ChatMessageController]의 `@MessageMapping`으로 라우팅된다.
 * - 구독(SUBSCRIBE) 목적지: `/topic`(방 단위 브로드캐스트), `/queue`(개인 수신).
 * - 사용자별 목적지 prefix는 `/user` (convertAndSendToUser → user 큐).
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
	private val authChannelInterceptor: AuthChannelInterceptor,
	// 앱 CORS와 동일한 프론트 오리진 목록. (api의 app.cors.allowed-origins를 그대로 읽어 WS 핸드셰이크 오리진을 제한한다)
	@Value("\${app.cors.allowed-origins}") private val allowedOrigins: List<String>,
) : WebSocketMessageBrokerConfigurer {

	override fun registerStompEndpoints(registry: StompEndpointRegistry) {
		// 핸드셰이크 단계에는 인증을 두지 않는다. 브라우저 WebSocket이 핸드셰이크에 토큰을 못 싣기 때문에,
		// 토큰 검증은 CONNECT 프레임(AuthChannelInterceptor)에서 수행한다. (핸드셰이크 경로는 SecurityConfig에서 permitAll)
		// 오리진은 와일드카드 대신 허용 목록으로 제한한다. (CORS 검증) WS 인증에 쿠키를 쓰지 않으므로 정확 일치(setAllowedOrigins)로 둔다.
		registry.addEndpoint("/ws/chat")
			.setAllowedOrigins(*allowedOrigins.toTypedArray())
			.withSockJS() // ws가 아닌 http 엔드포인트를 사용할 수 있게 해주는 sockJs 라이브러리를 허용
	}

	override fun configureMessageBroker(registry: MessageBrokerRegistry) {
		// 내장 simple broker(인메모리). 다중 인스턴스로 확장 시 외부 브로커(RabbitMQ STOMP relay)나 Redis 전파로 교체해야 한다.
		// topic/1 형태로 메세지를 수신해야 함을 설정
		registry.enableSimpleBroker("/topic", "/queue")
		// app/1 형태로 메세지를 발행해야 함 @Controller 객체의 @MessageMapping 메서드로 라우팅
		registry.setApplicationDestinationPrefixes("/app")
		registry.setUserDestinationPrefix("/user")
	}

	// 웹소켓 요청시에는 http header등 http 메세지를 넣어올 수 있고ㅡ 이를 interceptor를 통해 가로채 토큰등을 검증할 수 있다.
	override fun configureClientInboundChannel(registration: ChannelRegistration) {
		// CONNECT 등 STOMP 프레임 단계의 인증/처리는 인바운드 채널 인터셉터에서 수행한다.
		registration.interceptors(authChannelInterceptor)
	}
}
