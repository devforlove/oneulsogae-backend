package com.org.oneulsogae.domain.chat

import com.org.oneulsogae.common.chat.ChatMessageType
import com.org.oneulsogae.core.chat.ChatErrorCode
import com.org.oneulsogae.core.chat.command.domain.ChatMessage
import com.org.oneulsogae.core.common.error.BusinessException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [ChatMessage] 도메인 유닛 테스트.
 * 프레임워크·인프라 없이 순수 도메인 로직(본문 검증·생성)을 검증한다. 시각은 파라미터로 주입한다.
 */
class ChatMessageTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 6, 11, 12, 0)
	val chatRoomId: Long = 10L
	val senderId: Long = 1L

	describe("create") {
		it("본문·발신자·시각을 담은 신규 메세지를 생성한다") {
			val message: ChatMessage = ChatMessage.create(chatRoomId, senderId, "안녕하세요", now)

			message.chatRoomId shouldBe chatRoomId
			message.senderId shouldBe senderId
			message.content shouldBe "안녕하세요"
			message.sentAt shouldBe now
		}

		it("본문이 비어 있으면 EMPTY_MESSAGE를 던진다") {
			val exception: BusinessException = shouldThrow {
				ChatMessage.create(chatRoomId, senderId, "   ", now)
			}
			exception.errorCode shouldBe ChatErrorCode.EMPTY_MESSAGE
		}

		it("본문이 최대 길이를 넘으면 MESSAGE_TOO_LONG을 던진다") {
			val tooLong: String = "가".repeat(ChatMessage.MAX_CONTENT_LENGTH + 1)

			val exception: BusinessException = shouldThrow {
				ChatMessage.create(chatRoomId, senderId, tooLong, now)
			}
			exception.errorCode shouldBe ChatErrorCode.MESSAGE_TOO_LONG
		}

		it("본문이 최대 길이와 같으면 통과한다") {
			val maxLength: String = "가".repeat(ChatMessage.MAX_CONTENT_LENGTH)

			val message: ChatMessage = ChatMessage.create(chatRoomId, senderId, maxLength, now)

			message.content.length shouldBe ChatMessage.MAX_CONTENT_LENGTH
		}
	}

	describe("createSystem") {
		it("발신자 없는 SYSTEM 안내 메세지를 생성한다") {
			val message: ChatMessage = ChatMessage.createSystem(chatRoomId, "상대 팀이 매칭을 종료했어요", now)

			message.chatRoomId shouldBe chatRoomId
			message.senderId shouldBe null
			message.type shouldBe ChatMessageType.SYSTEM
			message.content shouldBe "상대 팀이 매칭을 종료했어요"
			message.sentAt shouldBe now
		}

		it("본문이 비어 있으면 EMPTY_MESSAGE를 던진다") {
			val exception: BusinessException = shouldThrow {
				ChatMessage.createSystem(chatRoomId, "  ", now)
			}
			exception.errorCode shouldBe ChatErrorCode.EMPTY_MESSAGE
		}
	}
})
