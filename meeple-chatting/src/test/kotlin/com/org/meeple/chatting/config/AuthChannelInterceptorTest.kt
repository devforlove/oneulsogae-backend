package com.org.meeple.chatting.config

import com.org.meeple.auth.PrincipalDetails
import com.org.meeple.auth.jwt.TokenProvider
import com.org.meeple.chatting.chat.application.ChatErrorCode
import com.org.meeple.chatting.chat.application.port.`in`.VerifyChatRoomParticipantUseCase
import com.org.meeple.chatting.chat.application.port.out.CheckActiveSessionPort
import com.org.meeple.chatting.common.error.ChatException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.web.socket.WebSocketSession

/**
 * [AuthChannelInterceptor]의 단일 활성 세션 대조 유닛 테스트.
 *
 * 동시 접속 차단은 CONNECT 시점에만 한다. 토큰의 session_id가 현재 활성 세션과 일치하는지 확인해,
 * 다른 브라우저의 새 로그인으로 밀려난 세션이면 CONNECT 연결을 거부(SESSION_TAKEN_OVER)함을 검증한다.
 * (실제 활성 세션 저장소(Redis)는 [CheckActiveSessionPort] 페이크로 대체)
 */
class AuthChannelInterceptorTest : DescribeSpec({

	val tokenProvider = TokenProvider(
		key = "test-secret-key-test-secret-key-test-secret-key-test-secret-key-0123456789",
		expireTime = "3600000",
		refreshExpireTime = "604800000",
	)

	// userId -> 현재 활성 sessionId. (Redis 마커를 대신하는 페이크)
	val activeSessions: MutableMap<Long, String> = mutableMapOf()
	val checkActiveSessionPort = object : CheckActiveSessionPort {
		override fun isActive(userId: Long, sessionId: String): Boolean = activeSessions[userId] == sessionId
	}
	val verifyParticipant = object : VerifyChatRoomParticipantUseCase {
		override fun verifyParticipant(userId: Long, chatRoomId: Long) = Unit
	}
	val noopChannel = MessageChannel { _: Message<*>, _: Long -> true }
	val messagingTemplate = SimpMessagingTemplate(noopChannel)
	val sessionRegistry = object : WebSocketSessionRegistry {
		override fun register(session: WebSocketSession) = Unit
		override fun unregister(wsSessionId: String) = Unit
		override fun bindAndEvictPrevious(userId: Long, jwtSessionId: String, wsSessionId: String) = Unit
	}

	val interceptor = AuthChannelInterceptor(
		tokenProvider = tokenProvider,
		verifyChatRoomParticipantUseCase = verifyParticipant,
		checkActiveSessionPort = checkActiveSessionPort,
		webSocketSessionRegistry = sessionRegistry,
		messagingTemplate = messagingTemplate,
	)

	fun authFor(userId: Long): Authentication {
		val principal = PrincipalDetails("u$userId@test.com", userId, listOf(SimpleGrantedAuthority("ROLE_USER")))
		return UsernamePasswordAuthenticationToken(principal, "", principal.authorities)
	}

	fun accessTokenFor(userId: Long, sessionId: String): String =
		tokenProvider.generateAccessToken(authFor(userId), sessionId)

	fun connectMessage(token: String): Message<*> {
		val accessor: StompHeaderAccessor = StompHeaderAccessor.create(StompCommand.CONNECT)
		accessor.setNativeHeader("Authorization", "Bearer $token")
		accessor.setLeaveMutable(true)
		return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
	}

	describe("CONNECT") {

		it("현재 활성 세션의 토큰이면 연결을 허용하고 Authentication을 주입한다") {
			activeSessions[1L] = "s1"
			val token: String = accessTokenFor(1L, "s1")

			val result: Message<*>? = interceptor.preSend(connectMessage(token), noopChannel)

			result.shouldNotBeNull()
			val user: Authentication? = StompHeaderAccessor.wrap(result).user as? Authentication
			(user?.principal as? PrincipalDetails)?.id shouldBe 1L
		}

		it("다른 브라우저의 새 로그인에 밀려난 세션이면 SESSION_TAKEN_OVER로 연결을 거부한다") {
			activeSessions[1L] = "s2" // 새 로그인이 활성 세션을 s2로 덮어씀
			val staleToken: String = accessTokenFor(1L, "s1") // 이전 브라우저의 토큰(서명은 유효)

			val exception: ChatException = shouldThrow {
				interceptor.preSend(connectMessage(staleToken), noopChannel)
			}
			exception.errorCode shouldBe ChatErrorCode.SESSION_TAKEN_OVER
		}
	}
})
