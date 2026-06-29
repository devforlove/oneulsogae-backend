package com.org.meeple.api.chat

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.delete
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.chat.ChatMessageType
import com.org.meeple.common.chat.ChatRoomMemberStatus
import com.org.meeple.common.chat.ChatRoomStatus
import com.org.meeple.common.match.MatchMemberStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.chat.command.entity.QChatMessageEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.meeple.infra.fixture.ChatRoomEntityFixture
import com.org.meeple.infra.fixture.ChatRoomMemberEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.SoloMatchEntityFixture
import com.org.meeple.infra.fixture.SoloMatchMemberEntityFixture
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchMemberEntity
import io.kotest.matchers.shouldBe

/**
 * `DELETE /chat/v1/rooms/{chatRoomId}/members` E2E 테스트.
 *
 * 현재 로그인 사용자가 채팅방에서 나간다. (요청자는 그 방의 참가자여야 한다)
 * 나가기는 본인의 참가자(ChatRoomMember) 행을 비활성(status=DEACTIVE)으로 전이하는 것이라, 본인 행만 비활성화되고 상대 참가자 행은 그대로여야 한다.
 */
class LeaveChatRoomE2ETest : AbstractIntegrationSupport({

	// 활성(status=ACTIVE) 참가자 행 존재 여부.
	fun activeMemberExists(chatRoomId: Long, userId: Long): Boolean {
		val member: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
		return IntegrationUtil.getQuery()
			.selectOne()
			.from(member)
			.where(
				member.chatRoomId.eq(chatRoomId),
				member.userId.eq(userId),
				member.status.eq(ChatRoomMemberStatus.ACTIVE),
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

	// 제거되지 않은(소프트 삭제 안 된) 매칭이 존재하는지. (@SQLRestriction으로 deleted_at is null 행만 조회된다)
	fun matchExists(matchId: Long): Boolean {
		val match: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
		return IntegrationUtil.getQuery()
			.selectOne()
			.from(match)
			.where(match.id.eq(matchId))
			.fetchFirst() != null
	}

	// 제거되지 않은(소프트 삭제 안 된) 방이 존재하는지. (@SQLRestriction으로 deleted_at is null 행만 조회된다)
	fun chatRoomExists(chatRoomId: Long): Boolean {
		val room: QChatRoomEntity = QChatRoomEntity.chatRoomEntity
		return IntegrationUtil.getQuery()
			.selectOne()
			.from(room)
			.where(room.id.eq(chatRoomId))
			.fetchFirst() != null
	}

	// 소프트 삭제 안 된 참가자 행이 하나라도 있는지. (방 종료 시 참가자 전체가 소프트 삭제되면 false)
	fun anyMemberExists(chatRoomId: Long): Boolean {
		val member: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
		return IntegrationUtil.getQuery()
			.selectOne()
			.from(member)
			.where(member.chatRoomId.eq(chatRoomId))
			.fetchFirst() != null
	}

	// 방에 남은 SYSTEM 메세지 본문 목록. (나감 안내 메세지 확인용)
	fun systemMessageContents(chatRoomId: Long): List<String> {
		val message: QChatMessageEntity = QChatMessageEntity.chatMessageEntity
		return IntegrationUtil.getQuery()
			.select(message.content)
			.from(message)
			.where(message.chatRoomId.eq(chatRoomId), message.type.eq(ChatMessageType.SYSTEM))
			.fetch()
	}

	// 참가자의 안 읽은 개수.
	fun unreadOf(chatRoomId: Long, userId: Long): Int {
		val member: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
		return IntegrationUtil.getQuery()
			.select(member.unreadCount)
			.from(member)
			.where(member.chatRoomId.eq(chatRoomId), member.userId.eq(userId))
			.fetchOne()!!
	}

	// 매칭 참가자의 현재 status. (소프트 삭제 안 된 행을 조회)
	fun matchMemberStatusOf(matchId: Long, userId: Long): MatchMemberStatus {
		val matchMember: QSoloMatchMemberEntity = QSoloMatchMemberEntity.soloMatchMemberEntity
		return IntegrationUtil.getQuery()
			.select(matchMember.status)
			.from(matchMember)
			.where(matchMember.matchId.eq(matchId), matchMember.userId.eq(userId))
			.fetchOne()!!
	}

	describe("DELETE /chat/v1/rooms/{chatRoomId}/members") {

		context("다른 참가자가 남아 있는 채팅방에서 한 명이 나가면") {
			it("본인만 비활성화하고, 나감 안내·안 읽음은 남기지 않으며(WebSocket이 처리), 방·매칭은 그대로 둔다 (200)") {
				val me = 9101L
				val partner = 9102L
				val matchId: Long = IntegrationUtil.persist(SoloMatchEntityFixture.create(memberKey = "9101-9102")).id!!
				IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = matchId, userId = me, gender = Gender.MALE, status = MatchMemberStatus.ACTIVE))
				IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = matchId, userId = partner, gender = Gender.FEMALE, status = MatchMemberStatus.ACTIVE))
				val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = matchId)).id!!
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me))
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = partner))

				delete("/chat/v1/rooms/$roomId/members") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("success", true)
				}

				// 본인 채팅 참가자 행은 비활성(DEACTIVE)으로 빠지고, 상대 행은 그대로(ACTIVE), 방도 ACTIVE
				activeMemberExists(roomId, me) shouldBe false
				activeMemberExists(roomId, partner) shouldBe true
				roomStatusOf(roomId) shouldBe ChatRoomStatus.ACTIVE
				// REST 나가기는 나감 안내(SYSTEM)·안 읽음을 남기지 않는다. (같은 안내를 WebSocket이 발행·브로드캐스트하므로 중복 저장을 막는다)
				systemMessageContents(roomId) shouldBe emptyList()
				unreadOf(roomId, partner) shouldBe 0
				unreadOf(roomId, me) shouldBe 0
				// 채팅방 나가기는 매칭을 건드리지 않는다 → 매칭·매칭 참가자 모두 유지(둘 다 ACTIVE)
				matchExists(matchId) shouldBe true
				matchMemberStatusOf(matchId, me) shouldBe MatchMemberStatus.ACTIVE
				matchMemberStatusOf(matchId, partner) shouldBe MatchMemberStatus.ACTIVE
			}
		}

		context("마지막 참가자가 나가 방이 닫혀도") {
			it("연결 매칭은 종료하지 않고 그대로 유지한다 (채팅과 매칭 분리) (200)") {
				val me = 9201L
				val partner = 9202L
				val matchId: Long = IntegrationUtil.persist(SoloMatchEntityFixture.create(memberKey = "9201-9202")).id!!
				IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = matchId, userId = me, gender = Gender.MALE, status = MatchMemberStatus.ACTIVE))
				IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = matchId, userId = partner, gender = Gender.FEMALE, status = MatchMemberStatus.ACTIVE))
				val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = matchId)).id!!
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me))
				// 상대는 이미 나간(비활성) 상태 → me가 마지막 활성 참가자라 나가면 방이 닫힌다.
				IntegrationUtil.persist(
					ChatRoomMemberEntityFixture.create(
						chatRoomId = roomId,
						userId = partner,
						status = ChatRoomMemberStatus.DEACTIVE,
					),
				)

				matchExists(matchId) shouldBe true

				delete("/chat/v1/rooms/$roomId/members") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("success", true)
				}

				// 마지막 참가자가 나가 방은 소프트 삭제되지만, 연결 매칭은 건드리지 않아 그대로 유지된다.
				chatRoomExists(roomId) shouldBe false
				matchExists(matchId) shouldBe true
			}
		}

		context("마지막 참가자가 나가면") {
			it("방과 참가자 전체를 종료하고 소프트 삭제한다 (200)") {
				val me = 9131L
				val partner = 9132L
				val roomId: Long = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = 94L)).id!!
				// 상대는 이미 나간(비활성, DEACTIVE) 상태로 준비 → me가 마지막 활성 참가자
				IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = me))
				IntegrationUtil.persist(
					ChatRoomMemberEntityFixture.create(
						chatRoomId = roomId,
						userId = partner,
						status = ChatRoomMemberStatus.DEACTIVE,
					),
				)

				delete("/chat/v1/rooms/$roomId/members") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("success", true)
				}

				// 마지막 참가자가 나갔으므로 방·참가자 전체가 소프트 삭제되어 더는 조회되지 않는다.
				chatRoomExists(roomId) shouldBe false
				anyMemberExists(roomId) shouldBe false
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

				// 본인 행이 비활성화돼 비참가자 취급 → 상세 조회 403
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
		IntegrationUtil.deleteAll(QChatMessageEntity.chatMessageEntity)
		IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
		IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
	}
})
