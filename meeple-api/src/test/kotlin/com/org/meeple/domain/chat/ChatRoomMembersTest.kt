package com.org.meeple.domain.chat

import com.org.meeple.core.chat.command.domain.ChatRoomMember
import com.org.meeple.core.chat.command.domain.ChatRoomMembers
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import java.time.LocalDateTime

/**
 * [ChatRoomMembers] 도메인 유닛 테스트.
 * 상대 식별([ChatRoomMembers.partnersOf])을 1:1·그룹챗·퇴장 참가자까지 검증한다. (참가 검증은 [ChatParticipants]가 담당)
 * 프레임워크·인프라 없이 순수 도메인 로직만 본다. 시각은 고정값으로 주입한다.
 */
class ChatRoomMembersTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 6, 11, 12, 0)
	val roomId: Long = 100L

	fun member(userId: Long, exited: Boolean = false): ChatRoomMember =
		ChatRoomMember.join(chatRoomId = roomId, userId = userId, now = now)
			.let { if (exited) it.exit(now.plusHours(1)) else it }

	describe("partnersOf") {
		it("1:1이면 나를 제외한 상대 한 명을 반환한다") {
			val members = ChatRoomMembers(listOf(member(1L), member(2L)))

			members.partnersOf(1L).map { it.userId } shouldContainExactlyInAnyOrder listOf(2L)
		}

		it("그룹챗이면 나를 제외한 여러 명을 반환한다") {
			val members = ChatRoomMembers(listOf(member(1L), member(2L), member(3L)))

			members.partnersOf(1L).map { it.userId } shouldContainExactlyInAnyOrder listOf(2L, 3L)
		}

		it("나간(exited) 참가자도 상대에 그대로 포함한다 (나감 여부로 거르지 않는다)") {
			val members = ChatRoomMembers(listOf(member(1L), member(2L), member(3L, exited = true)))

			members.partnersOf(1L).map { it.userId } shouldContainExactlyInAnyOrder listOf(2L, 3L)
		}
	}
})
