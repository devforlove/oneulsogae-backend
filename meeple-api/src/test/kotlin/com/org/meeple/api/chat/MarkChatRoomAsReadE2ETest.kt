package com.org.meeple.api.chat

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.put
import com.org.meeple.infra.chat.command.entity.QChatRoomEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.meeple.infra.fixture.ChatRoomEntityFixture
import com.org.meeple.infra.fixture.ChatRoomMemberEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import io.kotest.matchers.shouldBe

/**
 * `POST /chat/v1/rooms/{chatRoomId}/read` E2E 테스트.
 *
 * 특정 채팅방의 안 읽은 메세지 개수를 0으로 초기화한다. (요청자는 그 방의 참가자여야 한다)
 * 읽음 상태는 참가자(ChatRoomMember) 행 단위이므로, 요청자 본인 행만 0이 되고 같은 방 상대 참가자 행은 그대로여야 한다.
 */
class MarkChatRoomAsReadE2ETest : AbstractIntegrationSupport({

	// 요청자 본인의 안 읽은 개수를 다시 조회해 검증하기 위한 헬퍼.
	fun unreadCountOf(chatRoomId: Long, userId: Long): Int {
		val member: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
		return IntegrationUtil.getQuery()
			.select(member.unreadCount)
			.from(member)
			.where(member.chatRoomId.eq(chatRoomId), member.userId.eq(userId))
			.fetchOne()!!
	}

	// 요청자 본인의 읽음 포인터(lastReadMessageId)를 다시 조회해 검증하기 위한 헬퍼.
	fun lastReadMessageIdOf(chatRoomId: Long, userId: Long): Long? {
		val member: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
		return IntegrationUtil.getQuery()
			.select(member.lastReadMessageId)
			.from(member)
			.where(member.chatRoomId.eq(chatRoomId), member.userId.eq(userId))
			.fetchOne()
	}

	describe("POST /chat/v1/rooms/{chatRoomId}/read") {

		context("안 읽은 메세지가 쌓인 참가자가 읽음 처리를 요청하면") {
			it("본인의 안 읽은 개수만 0으로 초기화한다 (200)") {
				val me = 8101L
				val partner = 8102L
				val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = 81L)).id!!
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me, unreadCount = 5))
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = partner, unreadCount = 3))

				put("/chat/v1/rooms/$roomId/read") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("success", true)
				}

				// 본인 행은 0으로 초기화, 상대 행은 그대로
				unreadCountOf(roomId, me) shouldBe 0
				unreadCountOf(roomId, partner) shouldBe 3
			}
		}

		context("WS 읽음 경로로 읽음 포인터가 이미 전진된 참가자가 읽음 처리를 요청하면") {
			it("뱃지만 0이 되고 읽음 포인터(lastReadMessageId)는 유지된다 (200)") {
				val me = 8121L
				val partner = 8122L
				val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = 83L)).id!!
				// me의 읽음 포인터가 이미 20까지 전진해 있는 상태(WS 경로가 갱신해 둔 값)
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me, unreadCount = 5, lastReadMessageId = 20L))
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = partner, unreadCount = 3))

				put("/chat/v1/rooms/$roomId/read") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("success", true)
				}

				// 뱃지는 0으로 초기화되지만, 읽음 포인터는 REST 읽음 처리가 건드리지 않는다.
				// (엔티티 전체 머지였다면 동시 WS 전진을 옛 값으로 덮어 포인터가 역행할 수 있었다)
				unreadCountOf(roomId, me) shouldBe 0
				lastReadMessageIdOf(roomId, me) shouldBe 20L
			}
		}

		context("참가자가 아닌 사용자가 읽음 처리를 요청하면") {
			it("403(CHAT-002)을 반환한다") {
				val me = 8111L
				val partner = 8112L
				val strangerId = 8199L
				val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = 82L)).id!!
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me))
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = partner))

				put("/chat/v1/rooms/$roomId/read") {
					bearer(accessTokenFor(strangerId))
				} expect {
					status(403)
					body("success", false)
					body("error.code", "CHAT-002")
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				put("/chat/v1/rooms/1/read") {} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
	}
})
