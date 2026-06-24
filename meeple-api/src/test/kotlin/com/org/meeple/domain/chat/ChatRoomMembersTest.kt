package com.org.meeple.domain.chat

import com.org.meeple.common.chat.ChatRoomMemberStatus
import com.org.meeple.core.chat.command.domain.ChatRoomMember
import com.org.meeple.core.chat.command.domain.ChatRoomMembers
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [ChatRoomMembers] 일급 컬렉션 유닛 테스트.
 * 지정한 userId 참가자만 비활성화하는 동작을 검증한다.
 */
class ChatRoomMembersTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 6, 24, 12, 0)

	fun member(userId: Long): ChatRoomMember =
		ChatRoomMember(chatRoomId = 1L, userId = userId, joinedAt = now)

	describe("deactivate(userIds)") {
		it("지정한 userId 참가자만 DEACTIVE로 전이해 그 대상만 담아 돌려준다") {
			val members: ChatRoomMembers = ChatRoomMembers(listOf(member(1L), member(2L), member(3L)))

			val result: ChatRoomMembers = members.deactivate(setOf(1L, 3L))

			result.values.map { it.userId } shouldBe listOf(1L, 3L)
			result.values.all { it.status == ChatRoomMemberStatus.DEACTIVE } shouldBe true
		}

		it("대상이 없으면 빈 컬렉션을 돌려준다") {
			val members: ChatRoomMembers = ChatRoomMembers(listOf(member(1L)))

			members.deactivate(setOf(99L)).values shouldBe emptyList()
		}
	}
})
