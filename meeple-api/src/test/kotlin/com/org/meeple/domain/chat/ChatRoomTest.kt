package com.org.meeple.domain.chat

import com.org.meeple.common.chat.ChatRoomMatchType
import com.org.meeple.common.chat.ChatRoomStatus
import com.org.meeple.core.chat.ChatErrorCode
import com.org.meeple.core.chat.command.domain.ChatRoom
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.fixture.ChatRoomFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [ChatRoom] 도메인 유닛 테스트.
 * 프레임워크·인프라 없이 순수 도메인 로직(상태 전이·검증)을 검증한다. 시각은 파라미터로 주입한다.
 * 참가자(누가 방에 있는지)는 방이 아니라 [com.org.meeple.core.chat.command.domain.ChatRoomMembers]가 보관하므로, 참가 검증은 그쪽 테스트에서 다룬다.
 * 생성 자체(open)는 ChatRoom.open으로 검증하고, 그 외 동작의 시작 채팅방은 core testFixtures의 [ChatRoomFixture]로 만든다.
 */
class ChatRoomTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 6, 11, 12, 0)
	val matchId: Long = 42L

	describe("open") {
		it("매칭 타입·id와 ACTIVE 상태, now + EXPIRATION 만료 시각으로 생성한다") {
			val room: ChatRoom = ChatRoom.open(matchType = ChatRoomMatchType.TEAM, matchId = matchId, now = now)

			room.matchType shouldBe ChatRoomMatchType.TEAM
			room.matchId shouldBe matchId
			room.status shouldBe ChatRoomStatus.ACTIVE
			room.expiredAt shouldBe now.plus(ChatRoom.EXPIRATION)
			room.isClosed shouldBe false
		}

		it("마지막 메세지는 null로 시작한다") {
			val room: ChatRoom = ChatRoom.open(ChatRoomMatchType.SOLO, matchId, now)

			room.lastMessage shouldBe null
			room.lastMessageAt shouldBe null
		}
	}

	describe("validateNotClosed") {
		it("이미 종료된 채팅방이면 CHAT_ROOM_ALREADY_CLOSED를 던진다") {
			val room: ChatRoom = ChatRoomFixture.create().close()

			val exception: BusinessException = shouldThrow {
				room.validateNotClosed()
			}
			exception.errorCode shouldBe ChatErrorCode.CHAT_ROOM_ALREADY_CLOSED
		}

		it("활성 상태면 통과한다") {
			val room: ChatRoom = ChatRoomFixture.create()

			room.validateNotClosed()
		}
	}

	describe("isExpired") {
		it("만료 시각 이후면 true, 이전이면 false") {
			val room: ChatRoom = ChatRoomFixture.create()

			room.isExpired(room.expiredAt.minusSeconds(1)) shouldBe false
			room.isExpired(room.expiredAt) shouldBe true
			room.isExpired(room.expiredAt.plusSeconds(1)) shouldBe true
		}
	}

	describe("expire / close") {
		it("expire는 EXPIRED 상태로 전이하고 종료로 본다") {
			val room: ChatRoom = ChatRoomFixture.create().expire()

			room.status shouldBe ChatRoomStatus.EXPIRED
			room.isClosed shouldBe true
		}

		it("close는 CLOSED 상태로 전이하고 종료로 본다") {
			val room: ChatRoom = ChatRoomFixture.create().close()

			room.status shouldBe ChatRoomStatus.CLOSED
			room.isClosed shouldBe true
		}
	}

	describe("delete") {
		it("CLOSED로 전이하고 deletedAt을 채운다 (방 닫기 + 소프트 삭제)") {
			val room: ChatRoom = ChatRoomFixture.create().delete(now)

			room.status shouldBe ChatRoomStatus.CLOSED
			room.isClosed shouldBe true
			room.deletedAt shouldBe now
		}
	}

	describe("receiveMessage") {
		it("방의 마지막 메세지와 수신 시각만 갱신한다 (안 읽은 개수는 ChatRoomMember가 담당)") {
			val sentAt: LocalDateTime = now.plusMinutes(5)
			val room: ChatRoom = ChatRoomFixture.create()
				.receiveMessage(content = "안녕하세요", now = sentAt)

			room.lastMessage shouldBe "안녕하세요"
			room.lastMessageAt shouldBe sentAt
		}

		it("연속 수신 시 마지막 메세지가 최신으로 갱신된다") {
			val room: ChatRoom = ChatRoomFixture.create()
				.receiveMessage("1", now.plusMinutes(1))
				.receiveMessage("2", now.plusMinutes(2))

			room.lastMessage shouldBe "2"
			room.lastMessageAt shouldBe now.plusMinutes(2)
		}
	}
})
