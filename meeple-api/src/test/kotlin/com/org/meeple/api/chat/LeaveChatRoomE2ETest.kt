package com.org.meeple.api.chat

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.delete
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.chat.ChatRoomStatus
import com.org.meeple.infra.chat.command.entity.QChatRoomEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.meeple.infra.fixture.ChatRoomEntityFixture
import com.org.meeple.infra.fixture.ChatRoomMemberEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import io.kotest.matchers.shouldBe

/**
 * `DELETE /chat/v1/rooms/{chatRoomId}/members` E2E 테스트.
 *
 * 현재 로그인 사용자가 채팅방에서 나간다. (요청자는 그 방의 참가자여야 한다)
 * 나가기는 본인의 참가자(ChatRoomMember) 행을 소프트 삭제(deleted_at)하는 것이라, 본인 행만 빠지고 상대 참가자 행은 그대로여야 한다.
 */
class LeaveChatRoomE2ETest : AbstractIntegrationSupport({

	// 소프트 삭제되지 않은(@SQLRestriction 적용) 참가자 행 존재 여부.
	fun activeMemberExists(chatRoomId: Long, userId: Long): Boolean {
		val member: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
		return IntegrationUtil.getQuery()
			.selectOne()
			.from(member)
			.where(
				member.chatRoomId.eq(chatRoomId),
				member.userId.eq(userId),
				member.deletedAt.isNull,
			)
			.fetchFirst() != null
	}

	// 방의 현재 상태를 다시 조회한다.
	fun roomStatusOf(chatRoomId: Long): ChatRoomStatus {
		val room: QChatRoomEntity = QChatRoomEntity.chatRoomEntity
		return IntegrationUtil.getQuery()
			.select(room.status)
			.from(room)
			.where(room.id.eq(chatRoomId))
			.fetchOne()!!
	}

	describe("DELETE /chat/v1/rooms/{chatRoomId}/members") {

		context("다른 참가자가 남아 있는 채팅방에서 한 명이 나가면") {
			it("본인 행만 소프트 삭제하고 상대 행은 유지하며 방은 ACTIVE로 둔다 (200)") {
				val me = 9101L
				val partner = 9102L
				val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = 91L)).id!!
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me))
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = partner))

				delete("/chat/v1/rooms/$roomId/members") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("success", true)
				}

				// 본인 행은 빠지고(소프트 삭제), 상대 행은 그대로
				activeMemberExists(roomId, me) shouldBe false
				activeMemberExists(roomId, partner) shouldBe true
				// 남은 참가자가 있으므로 방은 유지(ACTIVE)
				roomStatusOf(roomId) shouldBe ChatRoomStatus.ACTIVE
			}
		}

		context("마지막 참가자가 나가면") {
			it("방을 CLOSED로 전이한다 (200)") {
				val me = 9131L
				val partner = 9132L
				val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = 94L)).id!!
				// 상대는 이미 나간(소프트 삭제된) 상태로 준비 → me가 마지막 참가자
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me))
				IntegrationUtil.persist(
					ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = partner)
						.also { it.softDelete() },
				)

				delete("/chat/v1/rooms/$roomId/members") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("success", true)
				}

				// 마지막 참가자가 나갔으므로 방은 종료(CLOSED)
				activeMemberExists(roomId, me) shouldBe false
				roomStatusOf(roomId) shouldBe ChatRoomStatus.CLOSED
			}
		}

		context("나간 사용자가 같은 방에 다시 접근하면") {
			it("더 이상 참가자가 아니므로 상세 조회가 403(CHAT-002)을 반환한다") {
				val me = 9111L
				val partner = 9112L
				val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = 92L)).id!!
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me))
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = partner))

				delete("/chat/v1/rooms/$roomId/members") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
				}

				// 나간 뒤에는 비참가자 취급 → 상세 조회 403
				get("/chat/v1/rooms/$roomId") {
					bearer(accessTokenFor(me))
				} expect {
					status(403)
					body("error.code", "CHAT-002")
				}
			}
		}

		context("참가자가 아닌 사용자가 나가기를 요청하면") {
			it("403(CHAT-002)을 반환한다") {
				val me = 9121L
				val partner = 9122L
				val strangerId = 9199L
				val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = 93L)).id!!
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me))
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = partner))

				delete("/chat/v1/rooms/$roomId/members") {
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
				delete("/chat/v1/rooms/1/members") {} expect {
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
