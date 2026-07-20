package com.org.oneulsogae.chatting.config

import com.org.oneulsogae.auth.jwt.TokenProvider
import com.org.oneulsogae.auth.userIdOrNull
import com.org.oneulsogae.chatting.chat.adapter.web.response.ChatErrorResponse
import com.org.oneulsogae.chatting.chat.application.ChatErrorCode
import com.org.oneulsogae.chatting.chat.application.port.`in`.VerifyChatRoomParticipantUseCase
import com.org.oneulsogae.chatting.common.error.ChatException
import org.springframework.context.annotation.Lazy
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessageType
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component

/**
 * STOMP 클라이언트 인바운드 채널 인터셉터.
 *
 * - CONNECT: `Authorization` 헤더의 JWT를 검증하고 인증 주체(Authentication)를 STOMP 세션에 주입한다.
 *   유효하지 않으면 예외를 던져 연결을 거부한다(ERROR 프레임). (핸드셰이크 경로는 SecurityConfig에서 permitAll)
 * - SUBSCRIBE: 방 브로드캐스트(`/topic/{chatRoomId}`) 구독 시 그 방의 참가자인지 인가한다.
 *   거부되면 **연결은 유지한 채** 그 SUBSCRIBE만 버리고, 사유를 발신자 개인 큐(`/user/queue/errors`)로 통지한다.
 *
 * principal→userId 추출은 [com.org.oneulsogae.auth.userIdOrNull], 목적지 파싱은 [ChatTopicDestination]에 위임해
 * 인터셉터에는 흐름 제어만 남긴다.
 *
 * `SimpMessagingTemplate`는 인바운드 채널 인터셉터 ↔ 브로커 설정 간 빈 생성 순환을 피하려 [Lazy]로 주입한다.
 */
@Component
class AuthChannelInterceptor(
	private val tokenProvider: TokenProvider,
	private val verifyChatRoomParticipantUseCase: VerifyChatRoomParticipantUseCase,
	@Lazy private val messagingTemplate: SimpMessagingTemplate,
) : ChannelInterceptor {

	override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
		// user를 주입하려면 message에 묶인 가변 accessor를 가져와야 한다. wrap()은 읽기 전용 복사본이라 변경이 반영되지 않는다.
		val accessor: StompHeaderAccessor =
			MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java) ?: return message

		return when (accessor.command) {
			StompCommand.CONNECT -> {
				authenticate(accessor)
				message
			}
			// 거부 시 null을 반환해 해당 SUBSCRIBE만 버린다. (연결은 유지)
			StompCommand.SUBSCRIBE -> authorizeSubscribe(accessor, message)
			else -> message
		}
	}

	// CONNECT: 토큰 검증 후 Authentication을 세션에 주입한다.
	private fun authenticate(accessor: StompHeaderAccessor) {
		val token: String = accessor.getFirstNativeHeader(AUTHORIZATION)?.removePrefix(BEARER_PREFIX)
			?: throw ChatException(ChatErrorCode.AUTHENTICATION_REQUIRED)
		if (!tokenProvider.validateToken(token)) {
			throw ChatException(ChatErrorCode.AUTHENTICATION_REQUIRED)
		}
		accessor.user = tokenProvider.getAuthentication(token)
	}

	// SUBSCRIBE: 방 구독은 그 방 참가자만 허용한다. 통과하면 원본 메세지를, 거부되면 개인 큐로 통지한 뒤 null(버림)을 반환한다.
	private fun authorizeSubscribe(accessor: StompHeaderAccessor, message: Message<*>): Message<*>? {
		val destination: String = accessor.destination ?: return message
		val topic = ChatTopicDestination(destination)
		if (!topic.isRoomTopic) {
			return message // 방 토픽이 아니면 인가 대상이 아니다.
		}
		return try {
			val userId: Long = accessor.user.userIdOrNull() ?: throw ChatException(ChatErrorCode.AUTHENTICATION_REQUIRED)
			val chatRoomId: Long = topic.chatRoomIdOrNull() ?: throw ChatException(ChatErrorCode.CHAT_ROOM_NOT_FOUND)
			verifyChatRoomParticipantUseCase.verifyParticipant(userId, chatRoomId)
			message
		} catch (e: ChatException) {
			notifySubscribeRejected(accessor, destination, e.errorCode)
			null
		}
	}

	// 구독 거부 사유를 발신자 개인 큐로 통지한다. 통지 대상(userId)/세션을 못 찾으면 조용히 버린다.
	// SEND의 @SendToUser(broadcast=false)와 동작을 통일: 거부를 일으킨 "그 세션(탭)"에만 보낸다.
	private fun notifySubscribeRejected(accessor: StompHeaderAccessor, destination: String, errorCode: ChatErrorCode) {
		val userId: Long = accessor.user.userIdOrNull() ?: return
		val sessionId: String = accessor.sessionId ?: return

		// simpSessionId를 실으면 해당 유저의 전 세션이 아니라 그 세션 한 곳으로만 해석된다.
		val sessionHeaders: SimpMessageHeaderAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE).apply {
			this.sessionId = sessionId
			setLeaveMutable(true)
		}

		messagingTemplate.convertAndSendToUser(
			userId.toString(),
			ERROR_QUEUE,
			ChatErrorResponse(code = errorCode.code, message = errorCode.message, destination = destination),
			sessionHeaders.messageHeaders,
		)
	}

	companion object {
		private const val AUTHORIZATION = "Authorization"
		private const val BEARER_PREFIX = "Bearer "

		/** 거부 등 개인 통지 목적지. 클라이언트는 `/user/queue/errors`를 구독한다. */
		private const val ERROR_QUEUE = "/queue/errors"
	}
}
