package com.org.meeple.api.match

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.common.chat.ChatRoomMemberStatus
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.common.user.Gender
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.delete
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.infra.alarm.command.entity.AlarmEntity
import com.org.meeple.infra.alarm.command.entity.QAlarmEntity
import com.org.meeple.infra.chat.command.entity.ChatRoomMemberEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.meeple.infra.fixture.ChatRoomEntityFixture
import com.org.meeple.infra.fixture.ChatRoomMemberEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.match.command.entity.MatchedTeamEntity
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QMatchedTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMatchEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.TeamMatchEntity
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 팀 해체 시 매칭 정리·채팅 차단·팀원 알림 E2E.
 * - 성사(MATCHED) 매칭: 그대로 유지, 나간 팀원의 chatroom_member만 DEACTIVE
 * - 미성사(PROPOSED) 매칭: CLOSED + matched_teams DEACTIVE
 * 알림 수신자는 해체 실행자를 제외한 같은 팀의 남은 구성원이며, 상대 팀은 받지 않는다.
 */
class DisbandTeamMatchTeardownE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	// 결성(ACTIVE)까지 진행한 팀의 teamId를 돌려준다. (초대 → 수락)
	fun formedTeam(ownerId: Long, invitedUserId: Long): Long {
		persistMatchUser(ownerId, Gender.MALE)
		persistMatchUser(invitedUserId, Gender.MALE)
		val teamId: Long = post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "regionId": 1, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()
		post("/teams/v1/$teamId/acceptance") { bearer(accessTokenFor(invitedUserId)) }
		return teamId
	}

	// 두 팀을 status로 묶은 팀 매칭을 만들고 teamMatchId를 돌려준다.
	fun persistTeamMatch(myTeamId: Long, opponentTeamId: Long, status: MatchStatus): Long {
		val header: TeamMatchEntity = IntegrationUtil.persist(
			TeamMatchEntity(
				memberKey = listOf(myTeamId, opponentTeamId).sorted().joinToString("-"),
				introducedDate = LocalDate.of(2026, 6, 24),
				expiresAt = LocalDateTime.of(2026, 6, 25, 12, 0),
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

	describe("DELETE /teams/v1/{teamId} — 매칭 정리") {

		context("성사(MATCHED) 매칭이 있는 팀을 해체하면") {
			it("매칭은 유지되고, 나간 팀원의 채팅 참가만 비활성화되며, 남은 팀원에게 알림이 간다") {
				val ownerId = 5001L
				val invitedUserId = 5002L
				val oppOwnerId = 5003L
				val oppInvitedUserId = 5004L
				val myTeamId: Long = formedTeam(ownerId, invitedUserId)
				val opponentTeamId: Long = formedTeam(oppOwnerId, oppInvitedUserId)
				val teamMatchId: Long = persistTeamMatch(myTeamId, opponentTeamId, MatchStatus.MATCHED)

				// 성사 매칭의 채팅방 + 양 팀 참가자 (matchId == teamMatchId 전제)
				val room = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = teamMatchId))
				val roomId: Long = room.id!!
				listOf(ownerId, invitedUserId, oppOwnerId, oppInvitedUserId).forEach { uid: Long ->
					IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = uid))
				}

				delete("/teams/v1/$myTeamId") { bearer(accessTokenFor(invitedUserId)) } expect {
					status(200)
					body("success", true)
				}

				// 매칭은 그대로 MATCHED
				teamMatchStatus(teamMatchId) shouldBe MatchStatus.MATCHED
				// 나간 팀원(내 팀)만 DEACTIVE, 상대 팀원은 ACTIVE 유지
				memberStatus(roomId, ownerId) shouldBe ChatRoomMemberStatus.DEACTIVE
				memberStatus(roomId, invitedUserId) shouldBe ChatRoomMemberStatus.DEACTIVE
				memberStatus(roomId, oppOwnerId) shouldBe ChatRoomMemberStatus.ACTIVE
				memberStatus(roomId, oppInvitedUserId) shouldBe ChatRoomMemberStatus.ACTIVE
				// 해체 실행자(invitedUserId) 제외, 남은 팀원(ownerId)에게만 알림. 상대 팀은 받지 않는다
				disbandAlarms(ownerId).size shouldBe 1
				disbandAlarms(invitedUserId).size shouldBe 0
				disbandAlarms(oppOwnerId).size shouldBe 0
				disbandAlarms(oppInvitedUserId).size shouldBe 0
			}
		}

		context("미성사(PROPOSED) 매칭이 있는 팀을 해체하면") {
			it("매칭이 CLOSED로 종료되고, 남은 팀원에게 알림이 간다") {
				val ownerId = 5101L
				val invitedUserId = 5102L
				val oppOwnerId = 5103L
				val oppInvitedUserId = 5104L
				val myTeamId: Long = formedTeam(ownerId, invitedUserId)
				val opponentTeamId: Long = formedTeam(oppOwnerId, oppInvitedUserId)
				val teamMatchId: Long = persistTeamMatch(myTeamId, opponentTeamId, MatchStatus.PROPOSED)

				delete("/teams/v1/$myTeamId") { bearer(accessTokenFor(invitedUserId)) } expect {
					status(200)
					body("success", true)
				}

				teamMatchStatus(teamMatchId) shouldBe MatchStatus.CLOSED
				matchedTeamStatus(teamMatchId, myTeamId) shouldBe MatchedTeamStatus.DEACTIVE
				matchedTeamStatus(teamMatchId, opponentTeamId) shouldBe MatchedTeamStatus.DEACTIVE
				// 해체 실행자(invitedUserId) 제외, 남은 팀원(ownerId)에게만 알림
				disbandAlarms(ownerId).size shouldBe 1
				disbandAlarms(invitedUserId).size shouldBe 0
				disbandAlarms(oppOwnerId).size shouldBe 0
				disbandAlarms(oppInvitedUserId).size shouldBe 0
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
		IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QMatchedTeamEntity.matchedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMatchEntity.teamMatchEntity)
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
	}
})

private fun teamMatchStatus(teamMatchId: Long): MatchStatus {
	val q = com.org.meeple.infra.match.command.entity.QTeamMatchEntity.teamMatchEntity
	return IntegrationUtil.getQuery().select(q.status).from(q).where(q.id.eq(teamMatchId)).fetchOne()!!
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

private fun disbandAlarms(userId: Long): List<AlarmEntity> {
	val q = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery().selectFrom(q)
		.where(q.userId.eq(userId).and(q.type.eq(AlarmType.TEAM_DISBANDED))).fetch()
}
