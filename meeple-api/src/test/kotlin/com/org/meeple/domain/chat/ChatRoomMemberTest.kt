package com.org.meeple.domain.chat

import com.org.meeple.core.chat.command.domain.ChatRoomMember
import com.org.meeple.core.fixture.ChatRoomMemberFixture
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [ChatRoomMember] 도메인 유닛 테스트.
 * 프레임워크·인프라 없이 순수 도메인 로직(참가 생성·읽음 상태 전이·퇴장)을 검증한다. 시각은 파라미터로 주입한다.
 * 참가 생성(join)은 ChatRoomMember.join으로 검증하고, 그 외 동작의 시작 참가자는 core testFixtures의 [ChatRoomMemberFixture]로 만든다.
 */
class ChatRoomMemberTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 6, 11, 12, 0)
	val chatRoomId: Long = 10L
	val userId: Long = 1L

	describe("join") {
		it("안 읽은 개수 0, 마지막 읽음 없음, 미퇴장 상태로 참가시킨다") {
			val member: ChatRoomMember = ChatRoomMember.join(chatRoomId = chatRoomId, userId = userId, now = now)

			member.chatRoomId shouldBe chatRoomId
			member.userId shouldBe userId
			member.unreadCount shouldBe 0
			member.lastReadAt shouldBe null
			member.joinedAt shouldBe now
			member.isExited shouldBe false
		}
	}

	describe("receiveMessage") {
		it("안 읽은 개수만 1 증가한다") {
			val member: ChatRoomMember = ChatRoomMemberFixture.create(unreadCount = 0)
				.receiveMessage()

			member.unreadCount shouldBe 1
		}

		it("연속 수신 시 안 읽은 개수가 누적된다") {
			val member: ChatRoomMember = ChatRoomMemberFixture.create(unreadCount = 0)
				.receiveMessage()
				.receiveMessage()
				.receiveMessage()

			member.unreadCount shouldBe 3
		}
	}

	describe("markAsRead") {
		it("안 읽은 개수를 0으로 되돌리고 마지막 읽은 시각을 갱신한다") {
			val member: ChatRoomMember = ChatRoomMemberFixture.create(unreadCount = 5)
				.markAsRead(now)

			member.unreadCount shouldBe 0
			member.lastReadAt shouldBe now
		}
	}

	describe("exit") {
		it("퇴장 시각을 채우고 나간 상태로 본다") {
			val member: ChatRoomMember = ChatRoomMemberFixture.create()
				.exit(now)

			member.exitedAt shouldBe now
			member.isExited shouldBe true
		}
	}

	describe("delete") {
		it("삭제 시각을 채우고 소프트 삭제(나가기) 상태로 본다") {
			val member: ChatRoomMember = ChatRoomMemberFixture.create()
				.delete(now)

			member.deletedAt shouldBe now
			member.isDeleted shouldBe true
		}

		it("나가기는 읽음 개수·퇴장 등 다른 상태를 건드리지 않는다") {
			val member: ChatRoomMember = ChatRoomMemberFixture.create(unreadCount = 3)
				.delete(now)

			member.unreadCount shouldBe 3
			member.isExited shouldBe false
		}
	}
})
