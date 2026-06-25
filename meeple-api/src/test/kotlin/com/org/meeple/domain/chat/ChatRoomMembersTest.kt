package com.org.meeple.domain.chat

import com.org.meeple.common.chat.ChatRoomMemberStatus
import com.org.meeple.core.chat.command.domain.ChatRoomMember
import com.org.meeple.core.chat.command.domain.ChatRoomMembers
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [ChatRoomMembers] 도메인 유닛 테스트.
 * 상대 식별([ChatRoomMembers.partnersOf]), 지정 userId 일괄 비활성화([ChatRoomMembers.deactivate]), 제외 대상 외 안 읽음 증가([ChatRoomMembers.receiveExcept])를 검증한다. (참가 검증은 [ChatParticipants]가 담당)
 * 프레임워크·인프라 없이 순수 도메인 로직만 본다. 시각은 고정값으로 주입한다.
 */
class ChatRoomMembersTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 6, 11, 12, 0)
	val roomId: Long = 100L

	fun member(userId: Long, exited: Boolean = false): ChatRoomMember =
		ChatRoomMember.join(chatRoomId = roomId, userId = userId, teamId = null, now = now)
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

	describe("receiveExcept(excludedUserIds)") {
		it("제외 대상이 아닌 활성 참가자만 안 읽은 개수를 1 올려 그 대상만 담아 돌려준다") {
			val members: ChatRoomMembers = ChatRoomMembers(listOf(member(1L), member(2L), member(3L)))

			val result: ChatRoomMembers = members.receiveExcept(setOf(1L))

			result.values.map { it.userId } shouldContainExactlyInAnyOrder listOf(2L, 3L)
			result.values.all { it.unreadCount == 1 } shouldBe true
		}

		it("비활성(DEACTIVE) 참가자는 제외 대상이 아니어도 올리지 않는다") {
			val deactivated: ChatRoomMember = member(2L).deactivate()
			val members: ChatRoomMembers = ChatRoomMembers(listOf(member(1L), deactivated))

			members.receiveExcept(setOf(1L)).values shouldBe emptyList()
		}
	}
})
