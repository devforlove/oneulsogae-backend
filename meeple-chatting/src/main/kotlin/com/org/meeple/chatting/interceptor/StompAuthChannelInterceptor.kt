package com.org.meeple.chatting.interceptor

import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component

/**
 * STOMP 클라이언트 인바운드 채널 인터셉터.
 *
 * 현재는 골격만 제공한다. CONNECT 프레임 시점에 토큰을 검증하고 인증 주체(Principal)를 세션에 주입하는 처리를
 * 추후 채워 넣는다. (핸드셰이크 단계 인증은 Security 필터가, STOMP 프레임 단계 인증은 이 인터셉터가 담당)
 */
@Component
class StompAuthChannelInterceptor : ChannelInterceptor {

	override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
		val accessor: StompHeaderAccessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
			?: return message

		if (StompCommand.CONNECT == accessor.command) {
			// TODO: accessor.getFirstNativeHeader("Authorization")에서 토큰을 꺼내 검증하고,
			//       accessor.user = 인증된 Principal 로 세팅한다. (이후 @MessageMapping에서 Principal 주입 가능)
		}

		return message
	}
}
