package com.org.meeple.domain.chat

import com.org.meeple.common.user.Gender
import com.org.meeple.core.chat.ChatErrorCode
import com.org.meeple.core.chat.query.dto.ChatParticipant
import com.org.meeple.core.chat.query.dto.ChatParticipants
import com.org.meeple.core.common.error.BusinessException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [ChatParticipants] 도메인 유닛 테스트.
 * 프로필을 동반한 참가자 목록의 참가 검증을 본다. 나감 여부는 보지 않으므로(기록용), 참가 이력이 있으면 통과한다.
 * 프레임워크·인프라 없이 순수 도메인 로직만 검증한다.
 */
class ChatParticipantsTest : DescribeSpec({

	fun participant(userId: Long): ChatParticipant =
		ChatParticipant(userId = userId, nickname = "user$userId", profileImageCode = null, gender = Gender.FEMALE)

	describe("validateParticipant") {
		it("참가자면 통과한다") {
			val participants = ChatParticipants(listOf(participant(1L), participant(2L), participant(3L)))

			participants.validateParticipant(3L)
		}

		it("참가자가 아니면 NOT_CHAT_ROOM_PARTICIPANT를 던진다") {
			val participants = ChatParticipants(listOf(participant(1L), participant(2L)))

			val exception: BusinessException = shouldThrow {
				participants.validateParticipant(999L)
			}
			exception.errorCode shouldBe ChatErrorCode.NOT_CHAT_ROOM_PARTICIPANT
		}
	}
})
