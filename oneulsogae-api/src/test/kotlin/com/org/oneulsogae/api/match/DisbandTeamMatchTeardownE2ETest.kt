package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.common.chat.ChatMessageType
import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.common.chat.ChatRoomMemberStatus
import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.common.match.MatchedTeamStatus
import com.org.oneulsogae.common.match.TeamMatchType
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.delete
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.infra.alarm.command.entity.AlarmEntity
import com.org.oneulsogae.infra.alarm.command.entity.QAlarmEntity
import com.org.oneulsogae.infra.chat.command.entity.ChatMessageEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatMessageEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.oneulsogae.infra.fixture.ChatRoomEntityFixture
import com.org.oneulsogae.infra.fixture.ChatRoomMemberEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.teammatch.command.entity.MatchedTeamEntity
import com.org.oneulsogae.infra.matchuser.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QMatchedTeamEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamMatchEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamMemberEntity
import com.org.oneulsogae.infra.teammatch.command.entity.TeamMatchEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 팀 해체(구성원 단위) 시 매칭 정리·채팅 차단·알림 E2E. (두 단계)
 * - 1단계(첫 구성원 탈퇴): 매칭/matched_team은 그대로 유지, 떠난 본인의 chatroom_member만 DEACTIVE(안내 메세지 없음), 남은 팀원에게 '팀 해체' 알림.
 * - 2단계(마지막 구성원 탈퇴): 우리 팀이 매칭에서 빠짐(matched_team DEACTIVE, 상대 팀은 유지). 떠난 본인 chatroom_member DEACTIVE,
 *   방에 남는 상대 팀에 "상대 팀이 매칭을 종료했어요" 안내 + '매칭 종료' 알림.
 */
class DisbandTeamMatchTeardownE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	// 결성(ACTIVE)까지 진행한 팀의 teamId를 돌려준다. (초대 → 수락)
	// 초대·수락 모두 회사 인증이 필요하므로 owner·invited 모두 인증된 프로필을 미리 채운다.
	fun formedTeam(ownerId: Long, invitedUserId: Long): Long {
		persistMatchUser(ownerId, Gender.MALE)
		persistMatchUser(invitedUserId, Gender.MALE)
		IntegrationUtil.persist(UserDetailEntityFixture.create(userId = ownerId, gender = Gender.MALE, companyName = "오늘소개"))
		IntegrationUtil.persist(UserDetailEntityFixture.create(userId = invitedUserId, gender = Gender.MALE, companyName = "오늘소개"))
		val teamId: Long = post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "regionId": 1, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()
		post("/teams/v1/$teamId/acceptance") { bearer(accessTokenFor(invitedUserId)) }
		return teamId
	}

	// 두 팀을 status로 묶은 팀 매칭을 만들고 teamMatchId를 돌려준다. (양 팀 matched_team은 ACTIVE)
	fun persistTeamMatch(myTeamId: Long, opponentTeamId: Long, status: MatchStatus): Long {
		val header: TeamMatchEntity = IntegrationUtil.persist(
			TeamMatchEntity(
				memberKey = listOf(myTeamId, opponentTeamId).sorted().joinToString("-"),
				introducedDate = LocalDate.of(2026, 6, 24),
				expiresAt = LocalDateTime.of(2999, 1, 1, 0, 0),
				status = status,
				matchType = TeamMatchType.RECOMMENDED,
				dateInitAmount = 40,
				dateAcceptAmount = 40,
			),
		)
		val teamMatchId: Long = header.id!!
		IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = myTeamId, status = MatchedTeamStatus.ACTIVE))
		IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = opponentTeamId, status = MatchedTeamStatus.ACTIVE))
		return teamMatchId
	}

	describe("DELETE /teams/v1/{teamId} — 매칭 정리 (두 단계)") {

		context("성사(MATCHED) 매칭이 있는 팀") {
			it("1단계는 매칭 유지+본인만 채팅 비활성(안내 없음), 2단계에 우리 팀이 매칭에서 빠지고 상대 팀에 종료 안내·알림이 간다") {
				val ownerId = 5001L
				val invitedUserId = 5002L
				val oppOwnerId = 5003L
				val oppInvitedUserId = 5004L
				val myTeamId: Long = formedTeam(ownerId, invitedUserId)
				val opponentTeamId: Long = formedTeam(oppOwnerId, oppInvitedUserId)
				val teamMatchId: Long = persistTeamMatch(myTeamId, opponentTeamId, MatchStatus.MATCHED)

				// 성사 매칭의 채팅방 + 양 팀 참가자 (matchId == teamMatchId 전제)
				val room = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchType = ChatRoomMatchType.TEAM, matchId = teamMatchId))
				val roomId: Long = room.id!!
				listOf(ownerId, invitedUserId, oppOwnerId, oppInvitedUserId).forEach { uid: Long ->
					IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = uid))
				}
				IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)

				// === 1단계: invitedUserId가 떠난다 ===
				delete("/teams/v1/$myTeamId") { bearer(accessTokenFor(invitedUserId)) } expect { status(200) }

				// 매칭/matched_team 그대로 유지
				teamMatchStatus(teamMatchId) shouldBe MatchStatus.MATCHED
				matchedTeamStatus(teamMatchId, myTeamId) shouldBe MatchedTeamStatus.ACTIVE
				matchedTeamStatus(teamMatchId, opponentTeamId) shouldBe MatchedTeamStatus.ACTIVE
				// 떠난 본인만 DEACTIVE, 남은 우리 팀원·상대 팀원은 ACTIVE
				memberStatus(roomId, invitedUserId) shouldBe ChatRoomMemberStatus.DEACTIVE
				memberStatus(roomId, ownerId) shouldBe ChatRoomMemberStatus.ACTIVE
				memberStatus(roomId, oppOwnerId) shouldBe ChatRoomMemberStatus.ACTIVE
				// 안내 메세지 없음(notifyRemaining=false), 상대 안 읽음 변화 없음
				chatMessages(roomId).filter { it.type == ChatMessageType.SYSTEM }.size shouldBe 0
				memberUnread(roomId, oppOwnerId) shouldBe 0
				// 남은 팀원(owner)에게만 '팀 해체' 알림, 상대 팀은 받지 않음
				disbandAlarms(ownerId).size shouldBe 1
				disbandAlarms(invitedUserId).size shouldBe 0
				matchEndedAlarms(oppOwnerId).size shouldBe 0

				// === 2단계: 남은 ownerId가 마저 떠난다 ===
				delete("/teams/v1/$myTeamId") { bearer(accessTokenFor(ownerId)) } expect { status(200) }

				// 우리 팀만 매칭에서 빠짐(상대 팀 유지) → 헤더는 MATCHED 유지
				teamMatchStatus(teamMatchId) shouldBe MatchStatus.MATCHED
				matchedTeamStatus(teamMatchId, myTeamId) shouldBe MatchedTeamStatus.DEACTIVE
				matchedTeamStatus(teamMatchId, opponentTeamId) shouldBe MatchedTeamStatus.ACTIVE
				// 우리 팀원 둘 다 DEACTIVE, 상대 팀원은 ACTIVE 유지
				memberStatus(roomId, ownerId) shouldBe ChatRoomMemberStatus.DEACTIVE
				memberStatus(roomId, oppOwnerId) shouldBe ChatRoomMemberStatus.ACTIVE
				memberStatus(roomId, oppInvitedUserId) shouldBe ChatRoomMemberStatus.ACTIVE
				// 방에 "상대 팀이 매칭을 종료했어요" 안내가 남고, 남는 상대 팀원의 안 읽음이 오른다
				val systemMessages: List<ChatMessageEntity> = chatMessages(roomId).filter { it.type == ChatMessageType.SYSTEM }
				systemMessages.size shouldBe 1
				systemMessages.first().content shouldBe "상대 팀이 매칭을 종료했어요"
				systemMessages.first().senderId shouldBe null
				memberUnread(roomId, oppOwnerId) shouldBe 1
				memberUnread(roomId, oppInvitedUserId) shouldBe 1
				// 상대 팀 구성원에게 '매칭 종료' 알림, fromTeamId는 떠난 우리 팀
				matchEndedAlarms(oppOwnerId).size shouldBe 1
				matchEndedAlarms(oppOwnerId).first().fromTeamId shouldBe myTeamId
				matchEndedAlarms(oppInvitedUserId).size shouldBe 1
			}
		}

		context("미성사(PROPOSED) 매칭이 있는 팀") {
			it("2단계에 우리 팀만 matched_team DEACTIVE로 빠지고(상대·헤더 유지), 상대 팀에 종료 알림이 간다") {
				val ownerId = 5101L
				val invitedUserId = 5102L
				val oppOwnerId = 5103L
				val oppInvitedUserId = 5104L
				val myTeamId: Long = formedTeam(ownerId, invitedUserId)
				val opponentTeamId: Long = formedTeam(oppOwnerId, oppInvitedUserId)
				val teamMatchId: Long = persistTeamMatch(myTeamId, opponentTeamId, MatchStatus.PROPOSED)
				IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)

				// 1단계: invited가 떠난다 (매칭은 그대로)
				delete("/teams/v1/$myTeamId") { bearer(accessTokenFor(invitedUserId)) } expect { status(200) }
				teamMatchStatus(teamMatchId) shouldBe MatchStatus.PROPOSED
				matchedTeamStatus(teamMatchId, myTeamId) shouldBe MatchedTeamStatus.ACTIVE

				// 2단계: 남은 owner가 마저 떠난다 → 우리 팀만 빠짐
				delete("/teams/v1/$myTeamId") { bearer(accessTokenFor(ownerId)) } expect { status(200) }

				teamMatchStatus(teamMatchId) shouldBe MatchStatus.PROPOSED
				matchedTeamStatus(teamMatchId, myTeamId) shouldBe MatchedTeamStatus.DEACTIVE
				matchedTeamStatus(teamMatchId, opponentTeamId) shouldBe MatchedTeamStatus.ACTIVE
				// 헤더(status)는 PROPOSED 그대로지만, 참가 팀만 바뀐 leave에서도 낙관적 락 버전이 전진한다(force-increment 없으면 0에 머묾).
				// → 같은 매칭에 동시에 들어온 관심/수락이 버전 충돌로 직렬화됨을 보장한다. (정확한 증가 횟수는 무관)
				teamMatchVersion(teamMatchId) shouldBeGreaterThan 0L
				// 상대 팀 구성원에게 '매칭 종료' 알림
				matchEndedAlarms(oppOwnerId).size shouldBe 1
				matchEndedAlarms(oppInvitedUserId).size shouldBe 1
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
		IntegrationUtil.deleteAll(QChatMessageEntity.chatMessageEntity)
		IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QMatchedTeamEntity.matchedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMatchEntity.teamMatchEntity)
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}
})

private fun teamMatchStatus(teamMatchId: Long): MatchStatus {
	val q = QTeamMatchEntity.teamMatchEntity
	return IntegrationUtil.getQuery().select(q.status).from(q).where(q.id.eq(teamMatchId)).fetchOne()!!
}

private fun teamMatchVersion(teamMatchId: Long): Long {
	val q = QTeamMatchEntity.teamMatchEntity
	return IntegrationUtil.getQuery().select(q.version).from(q).where(q.id.eq(teamMatchId)).fetchOne()!!
}

private fun matchedTeamStatus(teamMatchId: Long, teamId: Long): MatchedTeamStatus {
	val q = QMatchedTeamEntity.matchedTeamEntity
	return IntegrationUtil.getQuery().select(q.status).from(q)
		.where(q.teamMatchId.eq(teamMatchId).and(q.teamId.eq(teamId))).fetchOne()!!
}

private fun memberStatus(chatRoomId: Long, userId: Long): ChatRoomMemberStatus {
	val q = QChatRoomMemberEntity.chatRoomMemberEntity
	return IntegrationUtil.getQuery().select(q.status).from(q)
		.where(q.chatRoomId.eq(chatRoomId).and(q.userId.eq(userId))).fetchOne()!!
}

private fun chatMessages(chatRoomId: Long): List<ChatMessageEntity> {
	val q = QChatMessageEntity.chatMessageEntity
	return IntegrationUtil.getQuery().selectFrom(q).where(q.chatRoomId.eq(chatRoomId)).fetch()
}

private fun memberUnread(chatRoomId: Long, userId: Long): Int {
	val q = QChatRoomMemberEntity.chatRoomMemberEntity
	return IntegrationUtil.getQuery().select(q.unreadCount).from(q)
		.where(q.chatRoomId.eq(chatRoomId).and(q.userId.eq(userId))).fetchOne()!!
}

private fun disbandAlarms(userId: Long): List<AlarmEntity> {
	val q = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery().selectFrom(q)
		.where(q.userId.eq(userId).and(q.type.eq(AlarmType.TEAM_DISBANDED))).fetch()
}

private fun matchEndedAlarms(userId: Long): List<AlarmEntity> {
	val q = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery().selectFrom(q)
		.where(q.userId.eq(userId).and(q.type.eq(AlarmType.MANY_TO_MANY_MATCH_ENDED))).fetch()
}
