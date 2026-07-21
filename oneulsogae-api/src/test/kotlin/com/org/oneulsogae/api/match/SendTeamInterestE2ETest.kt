package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.common.match.MatchedTeamStatus
import com.org.oneulsogae.common.match.TeamMatchType
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.alarm.command.entity.AlarmEntity
import com.org.oneulsogae.infra.alarm.command.entity.QAlarmEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QRecommendedTeamHistoryEntity
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinHistoryEntity
import com.org.oneulsogae.infra.fixture.CoinBalanceEntityFixture
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
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * `POST /team-matches/v1/{teamMatchId}/interest` E2E. (팀 매칭 신청/수락 통합 엔드포인트)
 * 결성(ACTIVE)된 두 팀을 초대→수락으로 만들고, 두 팀을 묶은 팀 매칭을 준비한 뒤 관심을 보낸다.
 * 실서버(RANDOM_PORT) + Testcontainers(MySQL/Redis, 분산 락 포함)를 기동하고 HTTP를 호출한다.
 */
class SendTeamInterestE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	// 회사 인증을 마친(회사명이 채워진) 프로필. 팀 초대·수락·관심 보내기는 모두 회사 인증을 마친 사용자만 할 수 있다.
	fun persistVerifiedDetail(userId: Long) {
		IntegrationUtil.persist(
			UserDetailEntityFixture.create(userId = userId, gender = Gender.MALE, companyName = "오늘소개"),
		)
	}

	// 결성(ACTIVE)까지 진행한 팀의 teamId를 돌려준다. (초대 → 수락) 초대자·수락자 모두 회사 인증이 필요하다.
	fun formedTeam(ownerId: Long, invitedUserId: Long): Long {
		persistMatchUser(ownerId, Gender.MALE)
		persistMatchUser(invitedUserId, Gender.MALE)
		persistVerifiedDetail(ownerId)
		persistVerifiedDetail(invitedUserId)
		val teamId: Long = post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "regionId": 1, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()
		post("/teams/v1/$teamId/acceptance") { bearer(accessTokenFor(invitedUserId)) }
		return teamId
	}

	// 팀 결성 뒤 회사 인증이 취소된 상태를 재현한다. (formedTeam은 결성에 필요한 인증을 미리 채우므로,
	// 관심 보내기 자체의 게이트만 독립적으로 검증하려면 결성 후에 인증을 해제해야 한다)
	fun revokeCompanyVerification(userId: Long) {
		IntegrationUtil.update { queryFactory ->
			val detail = QUserDetailEntity.userDetailEntity
			queryFactory.update(detail).setNull(detail.companyName).where(detail.userId.eq(userId)).execute()
		}
	}

	// 두 팀을 참가 팀으로 하는 팀 매칭을 만들고 teamMatchId를 돌려준다. (각 팀 상태·헤더 상태 지정 가능)
	fun persistTeamMatch(
		myTeamId: Long,
		opponentTeamId: Long,
		headerStatus: MatchStatus = MatchStatus.PROPOSED,
		myStatus: MatchedTeamStatus = MatchedTeamStatus.WAITING,
		opponentStatus: MatchedTeamStatus = MatchedTeamStatus.WAITING,
	): Long {
		val header: TeamMatchEntity = IntegrationUtil.persist(
			TeamMatchEntity(
				memberKey = listOf(myTeamId, opponentTeamId).sorted().joinToString("-"),
				introducedDate = LocalDate.of(2026, 6, 24),
				expiresAt = LocalDateTime.of(2026, 6, 25, 12, 0),
				status = headerStatus,
				matchType = TeamMatchType.RECOMMENDED,
				dateInitAmount = 40,
				dateAcceptAmount = 40,
			),
		)
		val teamMatchId: Long = header.id!!
		IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = myTeamId, status = myStatus))
		IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = opponentTeamId, status = opponentStatus))
		return teamMatchId
	}

	describe("POST /team-matches/v1/{teamMatchId}/interest") {

		context("상대 팀이 아직 신청 안 한 팀 매칭에 관심을 보내면") {
			it("신청 비용(40)이 차감되고 PARTIALLY_ACCEPTED가 된다 + 상대 팀 2인에게 관심 알림, 채팅방 없음") {
				val myOwner = 6001L
				val myInvited = 6002L
				val oppOwner = 6003L
				val oppInvited = 6004L
				val myTeamId: Long = formedTeam(myOwner, myInvited)
				val opponentTeamId: Long = formedTeam(oppOwner, oppInvited)
				val teamMatchId: Long = persistTeamMatch(myTeamId, opponentTeamId)
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = myOwner, balance = 100))

				post("/team-matches/v1/$teamMatchId/interest") {
					bearer(accessTokenFor(myOwner))
				} expect {
					status(200)
					body("success", true)
					body("data.status", MatchStatus.PARTIALLY_ACCEPTED.name)
				}

				// 신청 비용(MEETING_INIT=40) 차감 → 잔액 60
				coinBalanceOf(myOwner) shouldBe 60
				// 미성사라 채팅방 없음 (matchId == teamMatchId 전제)
				chatRoomMemberCount(teamMatchId) shouldBe 0
				// 상대 팀 2인에게만 관심 알림, 내 팀은 받지 않음
				interestAlarms(oppOwner).size shouldBe 1
				interestAlarms(oppInvited).size shouldBe 1
				interestAlarms(myOwner).size shouldBe 0
				interestAlarms(myInvited).size shouldBe 0
				interestAlarms(oppOwner).first().fromTeamId shouldBe myTeamId
			}
		}

		context("상대 팀이 이미 신청한 팀 매칭에 관심을 보내면") {
			it("수락 비용(40)이 차감되고 MATCHED가 된다 + 4인 채팅방 생성 + 행위자 제외 3인에게 성사 알림") {
				val myOwner = 6101L
				val myInvited = 6102L
				val oppOwner = 6103L
				val oppInvited = 6104L
				val myTeamId: Long = formedTeam(myOwner, myInvited)
				val opponentTeamId: Long = formedTeam(oppOwner, oppInvited)
				// 상대 팀이 이미 APPLY한 PARTIALLY_ACCEPTED 매칭
				val teamMatchId: Long = persistTeamMatch(
					myTeamId = myTeamId,
					opponentTeamId = opponentTeamId,
					headerStatus = MatchStatus.PARTIALLY_ACCEPTED,
					myStatus = MatchedTeamStatus.WAITING,
					opponentStatus = MatchedTeamStatus.APPLY,
				)
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = myOwner, balance = 100))

				post("/team-matches/v1/$teamMatchId/interest") {
					bearer(accessTokenFor(myOwner))
				} expect {
					status(200)
					body("data.status", MatchStatus.MATCHED.name)
				}

				// 수락 비용(MEETING_ACCEPT=40) 차감 → 잔액 60
				coinBalanceOf(myOwner) shouldBe 60
				// 성사 → 4인 채팅방 생성 (matchId == teamMatchId)
				chatRoomMemberCount(teamMatchId) shouldBe 4
				// 행위자(myOwner) 제외한 3인에게 성사 알림
				matchedAlarms(myInvited).size shouldBe 1
				matchedAlarms(oppOwner).size shouldBe 1
				matchedAlarms(oppInvited).size shouldBe 1
				matchedAlarms(myOwner).size shouldBe 0
				// fromTeamId는 각 수신자의 상대 팀이다. 내 팀 동료(myInvited)는 상대 팀, 상대 팀원(oppOwner/oppInvited)은 내 팀.
				matchedAlarms(myInvited).first().fromTeamId shouldBe opponentTeamId
				matchedAlarms(oppOwner).first().fromTeamId shouldBe myTeamId
				matchedAlarms(oppInvited).first().fromTeamId shouldBe myTeamId
				// 성사 이력: 4인 각자 → 상대 팀 (재매칭 제외용)
				recommendedTeamHistoryTeamIds(myOwner) shouldContainExactlyInAnyOrder listOf(opponentTeamId)
				recommendedTeamHistoryTeamIds(myInvited) shouldContainExactlyInAnyOrder listOf(opponentTeamId)
				recommendedTeamHistoryTeamIds(oppOwner) shouldContainExactlyInAnyOrder listOf(myTeamId)
				recommendedTeamHistoryTeamIds(oppInvited) shouldContainExactlyInAnyOrder listOf(myTeamId)
			}
		}

		context("코인 잔액이 부족하면") {
			it("400(COIN-001)을 반환하고 잔액·매칭 상태가 그대로다") {
				val myOwner = 6201L
				val myInvited = 6202L
				val oppOwner = 6203L
				val oppInvited = 6204L
				val myTeamId: Long = formedTeam(myOwner, myInvited)
				val opponentTeamId: Long = formedTeam(oppOwner, oppInvited)
				val teamMatchId: Long = persistTeamMatch(myTeamId, opponentTeamId)
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = myOwner, balance = 10)) // 40보다 적음

				post("/team-matches/v1/$teamMatchId/interest") {
					bearer(accessTokenFor(myOwner))
				} expect {
					status(400)
					body("success", false)
					body("error.code", "COIN-001")
				}

				// 차감 실패로 롤백 → 잔액 유지 + 매칭 PROPOSED 그대로
				coinBalanceOf(myOwner) shouldBe 10
				teamMatchStatus(teamMatchId) shouldBe MatchStatus.PROPOSED
			}
		}

		context("참가 팀 구성원이 아닌 사용자가 관심을 보내면") {
			it("403(TEAM-MATCH-002)을 반환한다") {
				val myOwner = 6301L
				val myInvited = 6302L
				val oppOwner = 6303L
				val oppInvited = 6304L
				val outsider = 6399L
				val myTeamId: Long = formedTeam(myOwner, myInvited)
				val opponentTeamId: Long = formedTeam(oppOwner, oppInvited)
				val teamMatchId: Long = persistTeamMatch(myTeamId, opponentTeamId)
				persistMatchUser(outsider, Gender.MALE)
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = outsider, balance = 100))

				persistVerifiedDetail(outsider)
				post("/team-matches/v1/$teamMatchId/interest") {
					bearer(accessTokenFor(outsider))
				} expect {
					status(403)
					body("success", false)
					body("error.code", "TEAM-MATCH-002")
				}
			}
		}

		context("요청자가 회사 인증을 마치지 않았으면") {
			it("403(USER-035)을 반환하고 코인이 차감되지 않는다") {
				val myOwner = 6301L
				val myInvited = 6302L
				val oppOwner = 6303L
				val oppInvited = 6304L
				val myTeamId: Long = formedTeam(myOwner, myInvited)
				val opponentTeamId: Long = formedTeam(oppOwner, oppInvited)
				val teamMatchId: Long = persistTeamMatch(myTeamId, opponentTeamId)
				// 팀 결성에는 회사 인증이 필요해 formedTeam이 인증 프로필을 채운다. 이후 인증이 취소된 상태를 재현해
				// 관심 보내기 자체의 게이트(요청자 검증)만 독립적으로 검증한다.
				revokeCompanyVerification(myOwner)
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = myOwner, balance = 100))

				post("/team-matches/v1/$teamMatchId/interest") {
					bearer(accessTokenFor(myOwner))
				} expect {
					status(403)
					body("success", false)
					body("error.code", "USER-035")
				}

				// 차단이 코인 차감보다 앞이라 잔액이 그대로다.
				coinBalanceOf(myOwner) shouldBe 100
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				post("/team-matches/v1/1/interest") {} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QRecommendedTeamHistoryEntity.recommendedTeamHistoryEntity)
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
		IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
		IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
		IntegrationUtil.deleteAll(QMatchedTeamEntity.matchedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMatchEntity.teamMatchEntity)
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}
})

private fun coinBalanceOf(userId: Long): Int {
	val q = QCoinBalanceEntity.coinBalanceEntity
	return IntegrationUtil.getQuery().select(q.balance).from(q).where(q.userId.eq(userId)).fetchOne()!!
}

private fun teamMatchStatus(teamMatchId: Long): MatchStatus {
	val q = QTeamMatchEntity.teamMatchEntity
	return IntegrationUtil.getQuery().select(q.status).from(q).where(q.id.eq(teamMatchId)).fetchOne()!!
}

// matchId == teamMatchId인 채팅방의 참가자 수. (성사 시 4인 채팅방 생성 확인)
private fun chatRoomMemberCount(teamMatchId: Long): Long {
	val room = QChatRoomEntity.chatRoomEntity
	val member = QChatRoomMemberEntity.chatRoomMemberEntity
	return IntegrationUtil.getQuery()
		.select(member.count())
		.from(room)
		.join(member).on(member.chatRoomId.eq(room.id))
		.where(room.matchId.eq(teamMatchId))
		.fetchOne() ?: 0L
}

private fun interestAlarms(userId: Long): List<AlarmEntity> {
	val q = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery().selectFrom(q)
		.where(q.userId.eq(userId).and(q.type.eq(AlarmType.MANY_TO_MANY_INTEREST_RECEIVED))).fetch()
}

private fun matchedAlarms(userId: Long): List<AlarmEntity> {
	val q = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery().selectFrom(q)
		.where(q.userId.eq(userId).and(q.type.eq(AlarmType.MANY_TO_MANY_MATCHED))).fetch()
}

private fun recommendedTeamHistoryTeamIds(userId: Long): List<Long> {
	val q = QRecommendedTeamHistoryEntity.recommendedTeamHistoryEntity
	return IntegrationUtil.getQuery().select(q.teamId).from(q).where(q.userId.eq(userId)).fetch()
}
