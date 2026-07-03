package com.org.meeple.api.match

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.common.chat.ChatMessageType
import com.org.meeple.common.chat.ChatRoomMatchType
import com.org.meeple.common.chat.ChatRoomMemberStatus
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.delete
import com.org.meeple.common.integration.expect
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.alarm.command.entity.AlarmEntity
import com.org.meeple.infra.alarm.command.entity.QAlarmEntity
import com.org.meeple.infra.chat.command.entity.ChatMessageEntity
import com.org.meeple.infra.chat.command.entity.QChatMessageEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomEntity
import com.org.meeple.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.meeple.infra.fixture.ChatRoomEntityFixture
import com.org.meeple.infra.fixture.ChatRoomMemberEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.teammatch.command.entity.MatchedTeamEntity
import com.org.meeple.infra.teammatch.command.entity.QMatchedTeamEntity
import com.org.meeple.infra.teammatch.command.entity.QTeamEntity
import com.org.meeple.infra.teammatch.command.entity.QTeamMatchEntity
import com.org.meeple.infra.teammatch.command.entity.QTeamMemberEntity
import com.org.meeple.infra.teammatch.command.entity.TeamEntity
import com.org.meeple.infra.teammatch.command.entity.TeamMatchEntity
import com.org.meeple.infra.teammatch.command.entity.TeamMemberEntity
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import io.kotest.core.annotation.Ignored

/**
 * `DELETE /team-matches/v1/{teamMatchId}` E2E 테스트. (팀 매칭 종료 엔드포인트)
 *
 * 성사(MATCHED)된 팀 매칭을 한 팀이 종료하면, 그 팀의 matched_team만 DEACTIVE로 전이되고(소프트 삭제는 안 함, 상대 팀·헤더 유지),
 * 우리 팀원 전원이 채팅방에서 DEACTIVE가 되며 남는 상대 팀에 "상대 팀이 매칭을 종료했어요" 메세지와 "매칭 종료" 알림이 간다.
 * 상대 팀까지 모두 나간 마지막 종료에서는 team_matches 헤더와 참가 팀 전원이 CLOSED·소프트 삭제되고 알림은 가지 않는다.
 * 실제 서버(RANDOM_PORT) + Testcontainers(MySQL/Redis, 분산 락 포함)를 기동하고 HTTP를 호출한다.
 */
// [미팅 기능 미노출] 팀 매칭 컨트롤러(@RestController)가 주석 처리되어 엔드포인트가 404를 반환하므로 이 스펙을 비활성화한다.
// 기능 노출 시 컨트롤러 복구와 함께 @Ignored를 제거한다.
@Ignored
class EndTeamMatchE2ETest : AbstractIntegrationSupport({

	// 같은 성별 두 명으로 ACTIVE 팀을 만들고 teamId를 돌려준다.
	fun persistTeam(gender: Gender, vararg memberUserIds: Long): Long {
		val team: TeamEntity = IntegrationUtil.persist(
			TeamEntity(name = "팀", gender = gender, regionId = 1L, introduction = "소개", status = TeamStatus.ACTIVE),
		)
		val teamId: Long = team.id!!
		memberUserIds.forEach { uid: Long ->
			IntegrationUtil.persist(TeamMemberEntity(teamId = teamId, userId = uid, status = TeamMemberStatus.ACTIVE))
		}
		return teamId
	}

	// 두 팀을 MATCHED 팀 매칭(matched_teams 둘 다 ACTIVE)으로 묶고 teamMatchId를 돌려준다.
	fun persistMatchedTeamMatch(teamAId: Long, teamBId: Long): Long {
		val teamMatch: TeamMatchEntity = IntegrationUtil.persist(
			TeamMatchEntity(
				memberKey = listOf(teamAId, teamBId).sorted().joinToString("-"),
				introducedDate = LocalDate.of(2026, 6, 27),
				expiresAt = LocalDateTime.of(2126, 6, 27, 0, 0),
				status = MatchStatus.MATCHED,
				matchType = TeamMatchType.RECOMMENDED,
				dateInitAmount = 40,
				dateAcceptAmount = 40,
			),
		)
		val teamMatchId: Long = teamMatch.id!!
		IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = teamAId, status = MatchedTeamStatus.ACTIVE))
		IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = teamBId, status = MatchedTeamStatus.ACTIVE))
		return teamMatchId
	}

	// 팀 매칭에 연결된 4인 ACTIVE 채팅방을 만들고 채팅방 id를 돌려준다.
	fun persistChatRoom(teamMatchId: Long, teamAId: Long, teamAUsers: List<Long>, teamBId: Long, teamBUsers: List<Long>): Long {
		val room = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchType = ChatRoomMatchType.TEAM, matchId = teamMatchId))
		val roomId: Long = room.id!!
		teamAUsers.forEach { uid: Long -> IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = uid, teamId = teamAId)) }
		teamBUsers.forEach { uid: Long -> IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = uid, teamId = teamBId)) }
		return roomId
	}

	describe("DELETE /team-matches/v1/{teamMatchId}") {

		context("성사된 팀 매칭을 한 팀이 종료하면") {
			it("내 팀 matched_team만 비활성(DEACTIVE) 전이되고, 우리 팀원이 채팅방에서 나가며 상대 팀에 안내·알림이 간다 (200)") {
				val a1 = 4101L
				val a2 = 4102L
				val b1 = 5101L
				val b2 = 5102L
				val teamA: Long = persistTeam(Gender.MALE, a1, a2)
				val teamB: Long = persistTeam(Gender.FEMALE, b1, b2)
				val teamMatchId: Long = persistMatchedTeamMatch(teamA, teamB)
				val roomId: Long = persistChatRoom(teamMatchId, teamA, listOf(a1, a2), teamB, listOf(b1, b2))

				delete("/team-matches/v1/$teamMatchId") {
					bearer(accessTokenFor(a1))
				} expect {
					status(200)
					body("success", true)
				}

				// 내 팀(A) matched_team은 DEACTIVE로 전이(소프트 삭제 안 함), 상대 팀(B)은 ACTIVE 유지, 헤더는 MATCHED 유지
				matchedTeamStatus(teamMatchId, teamA) shouldBe MatchedTeamStatus.DEACTIVE
				matchedTeamStatus(teamMatchId, teamB) shouldBe MatchedTeamStatus.ACTIVE
				teamMatchStatus(teamMatchId) shouldBe MatchStatus.MATCHED
				// 우리 팀원 전원(A) 채팅 DEACTIVE, 상대 팀(B) ACTIVE 유지
				memberStatus(roomId, a1) shouldBe ChatRoomMemberStatus.DEACTIVE
				memberStatus(roomId, a2) shouldBe ChatRoomMemberStatus.DEACTIVE
				memberStatus(roomId, b1) shouldBe ChatRoomMemberStatus.ACTIVE
				memberStatus(roomId, b2) shouldBe ChatRoomMemberStatus.ACTIVE
				// 방에 "상대 팀이 매칭을 종료했어요" 시스템 메세지가 남는다
				val systemMessages: List<ChatMessageEntity> = chatMessages(roomId).filter { it.type == ChatMessageType.SYSTEM }
				systemMessages.size shouldBe 1
				systemMessages.first().content shouldBe "상대 팀이 매칭을 종료했어요"
				// 상대 팀 두 명에게 "매칭 종료" 알림(fromTeamId=나간 팀 A), 우리 팀엔 알림 없음
				alarmsOf(b1).map { it.type } shouldBe listOf(AlarmType.MANY_TO_MANY_MATCH_ENDED)
				alarmsOf(b1).first().fromTeamId shouldBe teamA
				alarmsOf(b1).first().description shouldBe "상대 팀이 매칭을 종료했어요."
				alarmsOf(b2).size shouldBe 1
				alarmsOf(a1).size shouldBe 0
			}
		}

		context("상대 팀이 이미 나간 뒤 마지막 팀이 종료하면") {
			it("team_matches 헤더가 CLOSED·소프트 삭제되고, 채팅방도 종료되며 알림이 가지 않는다 (200)") {
				val a1 = 4201L
				val a2 = 4202L
				val b1 = 5201L
				val b2 = 5202L
				val teamA: Long = persistTeam(Gender.MALE, a1, a2)
				val teamB: Long = persistTeam(Gender.FEMALE, b1, b2)
				val teamMatchId: Long = persistMatchedTeamMatch(teamA, teamB)
				val roomId: Long = persistChatRoom(teamMatchId, teamA, listOf(a1, a2), teamB, listOf(b1, b2))

				// 1차: 팀 A 종료 (헤더 유지, 상대 팀 B에 알림)
				delete("/team-matches/v1/$teamMatchId") { bearer(accessTokenFor(a1)) } expect { status(200) }
				teamMatchExists(teamMatchId) shouldBe true
				alarmsOf(b1).size shouldBe 1

				// 2차: 마지막 팀 B 종료 → 헤더 CLOSED·소프트 삭제, 채팅방 종료, 팀 A에 알림 없음
				delete("/team-matches/v1/$teamMatchId") { bearer(accessTokenFor(b1)) } expect { status(200) }
				teamMatchExists(teamMatchId) shouldBe false
				roomExists(roomId) shouldBe false
				alarmsOf(a1).size shouldBe 0
			}
		}

		context("아직 성사되지 않은(PARTIALLY_ACCEPTED) 팀 매칭을 종료하려 하면") {
			it("409(TEAM-MATCH-004)를 반환하고 매칭은 그대로다") {
				val a1 = 4301L
				val a2 = 4302L
				val b1 = 5301L
				val b2 = 5302L
				val teamA: Long = persistTeam(Gender.MALE, a1, a2)
				val teamB: Long = persistTeam(Gender.FEMALE, b1, b2)
				val teamMatch: TeamMatchEntity = IntegrationUtil.persist(
					TeamMatchEntity(
						memberKey = listOf(teamA, teamB).sorted().joinToString("-"),
						introducedDate = LocalDate.of(2026, 6, 27),
						expiresAt = LocalDateTime.of(2026, 6, 28, 0, 0),
						status = MatchStatus.PARTIALLY_ACCEPTED,
						matchType = TeamMatchType.RECOMMENDED,
						dateInitAmount = 40,
						dateAcceptAmount = 40,
					),
				)
				val teamMatchId: Long = teamMatch.id!!
				IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = teamA, status = MatchedTeamStatus.APPLY))
				IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = teamB, status = MatchedTeamStatus.WAITING))

				delete("/team-matches/v1/$teamMatchId") {
					bearer(accessTokenFor(a1))
				} expect {
					status(409)
					body("success", false)
					body("error.code", "TEAM-MATCH-004")
				}

				teamMatchStatus(teamMatchId) shouldBe MatchStatus.PARTIALLY_ACCEPTED
			}
		}

		context("참가 팀 구성원이 아닌 사용자가 종료하려 하면") {
			it("403(TEAM-MATCH-002)을 반환한다") {
				val a1 = 4401L
				val a2 = 4402L
				val b1 = 5401L
				val b2 = 5402L
				val strangerId = 9401L
				val teamA: Long = persistTeam(Gender.MALE, a1, a2)
				val teamB: Long = persistTeam(Gender.FEMALE, b1, b2)
				val teamMatchId: Long = persistMatchedTeamMatch(teamA, teamB)

				delete("/team-matches/v1/$teamMatchId") {
					bearer(accessTokenFor(strangerId))
				} expect {
					status(403)
					body("success", false)
					body("error.code", "TEAM-MATCH-002")
				}

				teamMatchStatus(teamMatchId) shouldBe MatchStatus.MATCHED
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				delete("/team-matches/v1/1") {} expect {
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
		IntegrationUtil.deleteAll(QMatchedTeamEntity.matchedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMatchEntity.teamMatchEntity)
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
	}
})

// @SQLRestriction("deleted_at is null") 적용 — 한 팀만 나간 동안은 DEACTIVE로 남고, 마지막 종료로 소프트 삭제되면 조회에서 빠져 null이 된다.
private fun matchedTeamStatus(teamMatchId: Long, teamId: Long): MatchedTeamStatus? {
	val q: QMatchedTeamEntity = QMatchedTeamEntity.matchedTeamEntity
	return IntegrationUtil.getQuery().select(q.status).from(q)
		.where(q.teamMatchId.eq(teamMatchId).and(q.teamId.eq(teamId))).fetchOne()
}

private fun teamMatchStatus(teamMatchId: Long): MatchStatus {
	val q: QTeamMatchEntity = QTeamMatchEntity.teamMatchEntity
	return IntegrationUtil.getQuery().select(q.status).from(q).where(q.id.eq(teamMatchId)).fetchOne()!!
}

// @SQLRestriction("deleted_at is null") 적용 — 소프트 삭제된 헤더는 조회에서 빠지므로 존재 여부로 종료를 확인한다.
private fun teamMatchExists(teamMatchId: Long): Boolean {
	val q: QTeamMatchEntity = QTeamMatchEntity.teamMatchEntity
	return IntegrationUtil.getQuery().select(q.id).from(q).where(q.id.eq(teamMatchId)).fetchOne() != null
}

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

private fun alarmsOf(userId: Long): List<AlarmEntity> {
	val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery().selectFrom(alarm).where(alarm.userId.eq(userId)).fetch()
}
