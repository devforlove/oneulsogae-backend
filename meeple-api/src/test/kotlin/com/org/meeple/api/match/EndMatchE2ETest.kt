package com.org.meeple.api.match

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.common.chat.ChatMessageType
import com.org.meeple.common.chat.ChatRoomMatchType
import com.org.meeple.common.chat.ChatRoomMemberStatus
import com.org.meeple.common.chat.ChatRoomStatus
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.delete
import com.org.meeple.common.integration.expect
import com.org.meeple.common.match.MatchMemberStatus
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.command.domain.MatchMembers
import com.org.meeple.infra.alarm.command.entity.AlarmEntity
import com.org.meeple.infra.alarm.command.entity.QAlarmEntity
import com.org.meeple.infra.chat.command.entity.ChatMessageEntity
import com.org.meeple.infra.chat.command.entity.QChatMessageEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.meeple.infra.fixture.ChatRoomEntityFixture
import com.org.meeple.infra.fixture.ChatRoomMemberEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.SoloMatchEntityFixture
import com.org.meeple.infra.fixture.SoloMatchMemberEntityFixture
import com.org.meeple.infra.match.command.entity.QSoloMatchEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchMemberEntity
import com.org.meeple.infra.match.command.entity.SoloMatchEntity
import io.kotest.matchers.shouldBe

/**
 * `DELETE /matches/v1/{matchId}` E2E 테스트. (매칭 종료 엔드포인트)
 *
 * 성사(MATCHED)된 매칭의 참가자가 나가면, 종료자 본인의 매칭 참가만 DEACTIVE가 되고(매칭은 유지) 상대도 모두 나간 뒤
 * 마지막 한 명이 나갈 때 비로소 매칭(헤더+참가자)이 소프트 삭제된다(@SQLRestriction로 조회에서 제외).
 * 연결된 채팅방에서는 종료자 본인만 DEACTIVE가 되며, 방에 남는 상대에게 "상대방이 매칭을 종료했어요" 시스템 메세지가 남는다.
 * 실제 서버(RANDOM_PORT) + Testcontainers(MySQL/Redis, 분산 락 포함)를 기동하고 HTTP를 호출한다.
 */
class EndMatchE2ETest : AbstractIntegrationSupport({

	// 1:1 매칭 헤더 + 두 참가자를 status로 함께 저장하고 헤더를 반환한다. (member_key는 참가자 조합과 일치시킨다)
	fun persistMatch(
		maleUserId: Long,
		femaleUserId: Long,
		status: MatchStatus = MatchStatus.MATCHED,
		memberStatus: MatchMemberStatus = MatchMemberStatus.ACTIVE,
	): SoloMatchEntity {
		val match: SoloMatchEntity = IntegrationUtil.persist(
			SoloMatchEntityFixture.create(
				memberKey = MatchMembers.memberKeyOf(listOf(maleUserId, femaleUserId)),
				status = status,
			),
		)
		val matchId: Long = match.id!!
		IntegrationUtil.persist(
			SoloMatchMemberEntityFixture.create(matchId = matchId, userId = maleUserId, gender = Gender.MALE, status = memberStatus),
		)
		IntegrationUtil.persist(
			SoloMatchMemberEntityFixture.create(matchId = matchId, userId = femaleUserId, gender = Gender.FEMALE, status = memberStatus),
		)
		return match
	}

	// 매칭에 연결된 ACTIVE 채팅방 + 두 참가자를 만들고 채팅방 id를 반환한다.
	fun persistChatRoom(matchId: Long, vararg userIds: Long): Long {
		val room = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchType = ChatRoomMatchType.SOLO, matchId = matchId))
		val roomId: Long = room.id!!
		userIds.forEach { uid: Long ->
			IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = uid))
		}
		return roomId
	}

	describe("DELETE /matches/v1/{matchId}") {

		context("성사된 매칭의 참가자 한쪽이 나가면") {
			it("종료자 본인의 매칭 참가만 비활성화되고(매칭 유지), 종료자만 채팅방에서 나가며 상대에게 나감 안내가 남는다 (200)") {
				val maleUserId = 1001L
				val femaleUserId = 2001L
				val match: SoloMatchEntity = persistMatch(maleUserId, femaleUserId)
				val matchId: Long = match.id!!
				val roomId: Long = persistChatRoom(matchId, maleUserId, femaleUserId)

				delete("/matches/v1/$matchId") {
					bearer(accessTokenFor(maleUserId))
				} expect {
					status(200)
					body("success", true)
				}

				// 매칭은 유지되고(헤더·참가자 그대로), 종료자(남성)의 매칭 참가만 DEACTIVE, 상대(여성)는 ACTIVE.
				matchExists(matchId) shouldBe true
				matchMemberCount(matchId) shouldBe 2L
				matchMemberStatus(matchId, maleUserId) shouldBe MatchMemberStatus.DEACTIVE
				matchMemberStatus(matchId, femaleUserId) shouldBe MatchMemberStatus.ACTIVE
				// 채팅방에서도 종료자(남성)만 DEACTIVE, 상대(여성)는 ACTIVE 유지
				memberStatus(roomId, maleUserId) shouldBe ChatRoomMemberStatus.DEACTIVE
				memberStatus(roomId, femaleUserId) shouldBe ChatRoomMemberStatus.ACTIVE
				// 방에 "상대방이 매칭을 종료했어요" 시스템 메세지가 남고, 남는 상대의 안 읽음이 오른다
				val systemMessages: List<ChatMessageEntity> =
					chatMessages(roomId).filter { it.type == ChatMessageType.SYSTEM }
				systemMessages.size shouldBe 1
				systemMessages.first().content shouldBe "상대방이 매칭을 종료했어요"
				systemMessages.first().senderId shouldBe null
				memberUnread(roomId, femaleUserId) shouldBe 1
				// 남는 상대(여성)에게 "매칭 종료" 알람이 발송된다 (alarm 도메인)
				val alarms: List<AlarmEntity> = alarmsOf(femaleUserId)
				alarms.size shouldBe 1
				alarms[0].type shouldBe AlarmType.ONE_TO_ONE_MATCH_ENDED
				alarms[0].fromUserId shouldBe maleUserId
				alarms[0].description shouldBe "상대방이 매칭을 종료했어요."
			}
		}

		context("성사된 매칭의 양쪽 참가자가 모두 나가면") {
			it("마지막 한 명이 나갈 때 매칭이 소프트 삭제되고, 마지막 종료자에게는 알람을 보내지 않는다 (200)") {
				val maleUserId = 1004L
				val femaleUserId = 2004L
				val match: SoloMatchEntity = persistMatch(maleUserId, femaleUserId)
				val matchId: Long = match.id!!
				val roomId: Long = persistChatRoom(matchId, maleUserId, femaleUserId)

				delete("/matches/v1/$matchId") { bearer(accessTokenFor(maleUserId)) } expect { status(200) }
				// 첫 종료(남성): 매칭·채팅방은 유지되고, 남는 상대(여성)에게 "매칭 종료" 알람 1건이 발송된다
				matchExists(matchId) shouldBe true
				roomStatus(roomId) shouldBe ChatRoomStatus.ACTIVE
				alarmsOf(femaleUserId).size shouldBe 1

				delete("/matches/v1/$matchId") { bearer(accessTokenFor(femaleUserId)) } expect { status(200) }
				// 마지막 종료(여성): 매칭이 소프트 삭제되고, 모든 참가자가 비활성이라 채팅방(헤더+참가자)도 종료(CLOSED)·소프트 삭제되며, 알릴 상대가 없어 종료자(남성)에게 알람이 가지 않는다
				matchExists(matchId) shouldBe false
				matchMemberCount(matchId) shouldBe 0L
				roomExists(roomId) shouldBe false
				alarmsOf(maleUserId).size shouldBe 0
			}
		}

		context("아직 성사되지 않은(PROPOSED) 매칭을 종료하려 하면") {
			it("409(MATCH-009)를 반환하고 매칭은 그대로다") {
				val maleUserId = 1002L
				val femaleUserId = 2002L
				val match: SoloMatchEntity = persistMatch(
					maleUserId = maleUserId,
					femaleUserId = femaleUserId,
					status = MatchStatus.PROPOSED,
					memberStatus = MatchMemberStatus.WAITING,
				)

				delete("/matches/v1/${match.id}") {
					bearer(accessTokenFor(maleUserId))
				} expect {
					status(409)
					body("success", false)
					body("error.code", "MATCH-009")
				}

				matchExists(match.id!!) shouldBe true
			}
		}

		context("참가자가 아닌 사용자가 종료하려 하면") {
			it("403(MATCH-002)을 반환한다") {
				val maleUserId = 1003L
				val femaleUserId = 2003L
				val strangerId = 9003L
				val match: SoloMatchEntity = persistMatch(maleUserId, femaleUserId)

				delete("/matches/v1/${match.id}") {
					bearer(accessTokenFor(strangerId))
				} expect {
					status(403)
					body("success", false)
					body("error.code", "MATCH-002")
				}

				matchExists(match.id!!) shouldBe true
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				delete("/matches/v1/1") {} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
		IntegrationUtil.deleteAll(QChatMessageEntity.chatMessageEntity)
		IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
		IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
	}
})

// @SQLRestriction("deleted_at is null")로 소프트 삭제된 매칭은 조회에서 빠지므로, 존재 여부로 소프트 삭제를 확인한다.
private fun matchExists(matchId: Long): Boolean {
	val q: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
	return IntegrationUtil.getQuery().select(q.id).from(q).where(q.id.eq(matchId)).fetchOne() != null
}

private fun matchMemberCount(matchId: Long): Long {
	val q: QSoloMatchMemberEntity = QSoloMatchMemberEntity.soloMatchMemberEntity
	return IntegrationUtil.getQuery().select(q.count()).from(q).where(q.matchId.eq(matchId)).fetchOne()!!
}

private fun matchMemberStatus(matchId: Long, userId: Long): MatchMemberStatus {
	val q: QSoloMatchMemberEntity = QSoloMatchMemberEntity.soloMatchMemberEntity
	return IntegrationUtil.getQuery().select(q.status).from(q)
		.where(q.matchId.eq(matchId).and(q.userId.eq(userId))).fetchOne()!!
}

private fun roomStatus(chatRoomId: Long): ChatRoomStatus {
	val q: QChatRoomEntity = QChatRoomEntity.chatRoomEntity
	return IntegrationUtil.getQuery().select(q.status).from(q).where(q.id.eq(chatRoomId)).fetchOne()!!
}

// @SQLRestriction("deleted_at is null")로 소프트 삭제된 방은 조회에서 빠지므로, 존재 여부로 종료(소프트 삭제)를 확인한다.
private fun roomExists(chatRoomId: Long): Boolean {
	val q: QChatRoomEntity = QChatRoomEntity.chatRoomEntity
	return IntegrationUtil.getQuery().select(q.id).from(q).where(q.id.eq(chatRoomId)).fetchOne() != null
}

private fun memberStatus(chatRoomId: Long, userId: Long): ChatRoomMemberStatus {
	val q: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
	return IntegrationUtil.getQuery().select(q.status).from(q)
		.where(q.chatRoomId.eq(chatRoomId).and(q.userId.eq(userId))).fetchOne()!!
}

private fun chatMessages(chatRoomId: Long): List<ChatMessageEntity> {
	val q: QChatMessageEntity = QChatMessageEntity.chatMessageEntity
	return IntegrationUtil.getQuery().selectFrom(q).where(q.chatRoomId.eq(chatRoomId)).fetch()
}

// 해당 사용자의 알람 목록. (매칭 종료 알람 발송 확인용)
private fun alarmsOf(userId: Long): List<AlarmEntity> {
	val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery().selectFrom(alarm).where(alarm.userId.eq(userId)).fetch()
}

private fun memberUnread(chatRoomId: Long, userId: Long): Int {
	val q: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
	return IntegrationUtil.getQuery().select(q.unreadCount).from(q)
		.where(q.chatRoomId.eq(chatRoomId).and(q.userId.eq(userId))).fetchOne()!!
}
