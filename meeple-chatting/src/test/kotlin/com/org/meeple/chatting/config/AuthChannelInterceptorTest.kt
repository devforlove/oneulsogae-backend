package com.org.meeple.chatting.config

import com.org.meeple.auth.PrincipalDetails
import com.org.meeple.auth.jwt.TokenProvider
import com.org.meeple.chatting.chat.application.ChatErrorCode
import com.org.meeple.chatting.chat.application.port.`in`.VerifyChatRoomParticipantUseCase
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

/**
 * [AuthChannelInterceptor]의 CONNECT 인증 유닛 테스트.
 * 유효한 JWT면 Authentication을 STOMP 세션에 주입하고, 없거나 유효하지 않으면 연결을 거부함을 검증한다.
 */
class AuthChannelInterceptorTest : DescribeSpec({

	val tokenProvider = TokenProvider(
		key = "test-secret-key-test-secret-key-test-secret-key-test-secret-key-0123456789",
		expireTime = "3600000",
		refreshExpireTime = "604800000",
	)

	val verifyParticipant = object : VerifyChatRoomParticipantUseCase {
		override fun verifyParticipant(userId: Long, chatRoomId: Long) = Unit
	}
	val noopChannel = MessageChannel { _: Message<*>, _: Long -> true }
	val messagingTemplate = SimpMessagingTemplate(noopChannel)

	val interceptor = AuthChannelInterceptor(
		tokenProvider = tokenProvider,
		verifyChatRoomParticipantUseCase = verifyParticipant,
		messagingTemplate = messagingTemplate,
	)

	fun authFor(userId: Long): Authentication {
		val principal = PrincipalDetails("u$userId@test.com", userId, listOf(SimpleGrantedAuthority("ROLE_USER")))
		return UsernamePasswordAuthenticationToken(principal, "", principal.authorities)
	}

	fun connectMessage(token: String?): Message<*> {
		val accessor: StompHeaderAccessor = StompHeaderAccessor.create(StompCommand.CONNECT)
		token?.let { accessor.setNativeHeader("Authorization", "Bearer $it") }
		accessor.setLeaveMutable(true)
		return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
	}

	describe("CONNECT") {

		it("유효한 토큰이면 연결을 허용하고 Authentication을 주입한다") {
			val token: String = tokenProvider.generateAccessToken(authFor(1L))

			val result: Message<*>? = interceptor.preSend(connectMessage(token), noopChannel)

			result.shouldNotBeNull()
			val user: Authentication? = StompHeaderAccessor.wrap(result).user as? Authentication
			(user?.principal as? PrincipalDetails)?.id shouldBe 1L
		}

		it("토큰이 없으면 AUTHENTICATION_REQUIRED로 연결을 거부한다") {
			val exception: ChatException = shouldThrow {
				interceptor.preSend(connectMessage(null), noopChannel)
			}
			exception.errorCode shouldBe ChatErrorCode.AUTHENTICATION_REQUIRED
		}

		it("유효하지 않은 토큰이면 AUTHENTICATION_REQUIRED로 연결을 거부한다") {
			val exception: ChatException = shouldThrow {
				interceptor.preSend(connectMessage("invalid-token"), noopChannel)
			}
			exception.errorCode shouldBe ChatErrorCode.AUTHENTICATION_REQUIRED
		}
	}
})
