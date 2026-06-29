package com.org.meeple.domain.match

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.teammatch.TeamMatchErrorCode
import com.org.meeple.core.teammatch.command.domain.TeamMatch
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime

/**
 * [TeamMatch] 도메인 유닛 테스트.
 * 프레임워크·인프라 없이 순수 도메인 로직(팀 매칭 생성, 멤버 키 산출)을 검증한다.
 */
class TeamMatchTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 6, 24, 12, 0)

	describe("propose - 팀 매칭 생성") {
		it("두 팀을 참가 팀으로 담아 PROPOSED 상태의 팀 매칭을 생성한다") {
			val teamMatch: TeamMatch = TeamMatch.propose(
				teamAId = 10L,
				teamBId = 20L,
				matchType = TeamMatchType.RECOMMENDED,
				now = now,
			)

			teamMatch.status shouldBe MatchStatus.PROPOSED
			teamMatch.matchType shouldBe TeamMatchType.RECOMMENDED
			teamMatch.introducedDate shouldBe now.toLocalDate()
			teamMatch.expiresAt shouldBe now.plusDays(1)
			teamMatch.dateInitAmount shouldBe 40
			teamMatch.dateAcceptAmount shouldBe 40
			teamMatch.matchedTeams.teamIds() shouldBe listOf(10L, 20L)
			teamMatch.matchedTeams.values.all { it.status == MatchedTeamStatus.WAITING } shouldBe true
		}

		it("matchedTeamsWith는 참가 팀 전원에 teamMatchId를 채워 돌려준다") {
			val teamMatch: TeamMatch = TeamMatch.propose(
				teamAId = 10L,
				teamBId = 20L,
				matchType = TeamMatchType.RECOMMENDED,
				now = now,
			)

			teamMatch.matchedTeamsWith(5L).values.all { it.teamMatchId == 5L } shouldBe true
			// 원본은 그대로(불변)
			teamMatch.matchedTeams.values.all { it.teamMatchId == 0L } shouldBe true
		}

		it("memberKey는 두 teamId를 정렬해 '-'로 잇는다 (순서 무관)") {
			val teamMatch: TeamMatch = TeamMatch.propose(
				teamAId = 20L,
				teamBId = 10L,
				matchType = TeamMatchType.RECOMMENDED,
				now = now,
			)

			teamMatch.memberKey() shouldBe "10-20"
		}

		it("teamIds는 참가 팀 id 목록을 돌려준다") {
			val teamMatch: TeamMatch = TeamMatch.propose(
				teamAId = 10L,
				teamBId = 20L,
				matchType = TeamMatchType.RECOMMENDED,
				now = now,
			)

			teamMatch.teamIds() shouldBe listOf(10L, 20L)
		}
	}

	describe("close - 미성사 매칭 종료") {
		it("status를 CLOSED로 바꾸고 참가 팀 전원을 DEACTIVE로 전이한다") {
			val teamMatch: TeamMatch = TeamMatch.propose(
				teamAId = 10L,
				teamBId = 20L,
				matchType = TeamMatchType.RECOMMENDED,
				now = now,
			)

			val closed: TeamMatch = teamMatch.close()

			closed.status shouldBe MatchStatus.CLOSED
			closed.matchedTeams.values.all { it.status == MatchedTeamStatus.DEACTIVE } shouldBe true
		}
	}

	describe("isMatched") {
		it("isMatched는 status가 MATCHED일 때만 true다") {
			val proposed: TeamMatch = TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now)

			proposed.isMatched() shouldBe false
			proposed.copy(status = MatchStatus.MATCHED).isMatched() shouldBe true
		}
	}

	describe("respond - 팀 관심 신청/성사") {
		it("한 팀만 신청하면 그 팀이 APPLY, 매칭은 PARTIALLY_ACCEPTED가 된다") {
			val teamMatch: TeamMatch = TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now)

			val responded: TeamMatch = teamMatch.respond(10L, 100L)

			responded.status shouldBe MatchStatus.PARTIALLY_ACCEPTED
			responded.matchedTeams.values.first { it.teamId == 10L }.status shouldBe MatchedTeamStatus.APPLY
			responded.matchedTeams.values.first { it.teamId == 20L }.status shouldBe MatchedTeamStatus.WAITING
			// 미성사는 만료 시각 그대로
			responded.expiresAt shouldBe now.plusDays(1)
		}

		it("양 팀이 모두 신청하면 MATCHED + 전원 ACTIVE가 되고 만료가 100년 연장된다") {
			val teamMatch: TeamMatch = TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now)

			val matched: TeamMatch = teamMatch.respond(10L, 100L).respond(20L, 200L)

			matched.status shouldBe MatchStatus.MATCHED
			matched.matchedTeams.values.all { it.status == MatchedTeamStatus.ACTIVE } shouldBe true
			matched.expiresAt shouldBe now.plusDays(1).plusYears(100)
		}
	}

	describe("validateRespondable") {
		it("참가 팀이 아니면 NOT_TEAM_MATCH_PARTICIPANT를 던진다") {
			val teamMatch: TeamMatch = TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now)

			val ex: BusinessException = shouldThrow { teamMatch.validateRespondable(99L) }
			ex.errorCode shouldBe TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT
		}

		it("이미 종료(CLOSED)된 매칭이면 TEAM_MATCH_ALREADY_CLOSED를 던진다") {
			val closed: TeamMatch = TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now).close()

			val ex: BusinessException = shouldThrow { closed.validateRespondable(10L) }
			ex.errorCode shouldBe TeamMatchErrorCode.TEAM_MATCH_ALREADY_CLOSED
		}
	}

	describe("validateTerminable - 팀 매칭 종료 검증") {
		// 양 팀 신청으로 성사(MATCHED)된 팀 매칭
		fun matched(): TeamMatch =
			TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now).respond(10L, 100L).respond(20L, 200L)

		it("참가 팀이 아니면 NOT_TEAM_MATCH_PARTICIPANT를 던진다") {
			val ex: BusinessException = shouldThrow { matched().validateTerminable(99L) }
			ex.errorCode shouldBe TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT
		}

		it("이미 종료(CLOSED)된 매칭이면 TEAM_MATCH_ALREADY_CLOSED를 던진다") {
			val closed: TeamMatch = matched().copy(status = MatchStatus.CLOSED)
			val ex: BusinessException = shouldThrow { closed.validateTerminable(10L) }
			ex.errorCode shouldBe TeamMatchErrorCode.TEAM_MATCH_ALREADY_CLOSED
		}

		it("성사(MATCHED) 전이면 TEAM_MATCH_NOT_MATCHED를 던진다") {
			val partiallyAccepted: TeamMatch = TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now).respond(10L, 100L)
			val ex: BusinessException = shouldThrow { partiallyAccepted.validateTerminable(10L) }
			ex.errorCode shouldBe TeamMatchErrorCode.TEAM_MATCH_NOT_MATCHED
		}

		it("성사된 매칭의 참가 팀이면 예외 없이 통과한다") {
			matched().validateTerminable(10L)
		}

		it("이미 나간(비활성) 팀이 다시 종료하려 하면 TEAM_MATCH_ALREADY_CLOSED를 던진다") {
			// 10번 팀이 먼저 나가 헤더는 MATCHED로 남고 10번만 DEACTIVE인 상태
			val afterTenLeft: TeamMatch = matched().leave(10L, now)

			val ex: BusinessException = shouldThrow { afterTenLeft.validateTerminable(10L) }
			ex.errorCode shouldBe TeamMatchErrorCode.TEAM_MATCH_ALREADY_CLOSED
		}
	}

	describe("isLastActiveTeam") {
		fun matched(): TeamMatch =
			TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now).respond(10L, 100L).respond(20L, 200L)

		it("상대 팀이 활성이면 false다") {
			matched().isLastActiveTeam(10L) shouldBe false
		}
	}

	describe("leave - 팀 매칭 종료") {
		fun matched(): TeamMatch =
			TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now).respond(10L, 100L).respond(20L, 200L)

		it("상대 팀이 활성이면 내 팀만 DEACTIVE로 전이되고(소프트 삭제 안 함) 헤더는 MATCHED로 유지된다") {
			val left: TeamMatch = matched().leave(10L, now)

			left.status shouldBe MatchStatus.MATCHED
			val ten: com.org.meeple.core.teammatch.command.domain.MatchedTeam = left.matchedTeams.values.first { it.teamId == 10L }
			ten.status shouldBe MatchedTeamStatus.DEACTIVE
			ten.deletedAt shouldBe null
			left.matchedTeams.values.first { it.teamId == 20L }.status shouldBe MatchedTeamStatus.ACTIVE
			left.deletedAt shouldBe null
		}

		it("상대 팀이 이미 나간 마지막 종료면 헤더와 참가 팀 전원이 CLOSED+deletedAt이 된다") {
			val onlyTenActive: TeamMatch = matched().leave(20L, now)

			val closed: TeamMatch = onlyTenActive.leave(10L, now)

			closed.status shouldBe MatchStatus.CLOSED
			closed.deletedAt shouldBe now
			closed.matchedTeams.values.all { it.status == MatchedTeamStatus.DEACTIVE } shouldBe true
			closed.matchedTeams.values.all { it.deletedAt == now } shouldBe true
		}
	}

	describe("failureRefunds - 미성사 만료 환불 산정") {
		it("신청(APPLY)한 팀의 지불자에게만 신청 비용의 절반을 환불한다") {
			// teamA(10) 지불자 userId=100이 신청, teamB(20)는 미신청
			val partiallyAccepted: TeamMatch =
				TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now).respond(10L, 100L)

			val refunds: List<com.org.meeple.core.teammatch.command.domain.MatchRefund> = partiallyAccepted.failureRefunds()

			refunds.size shouldBe 1
			refunds.first().userId shouldBe 100L
			refunds.first().amount shouldBe 20 // MEETING_INIT(40)의 절반
		}

		it("아무도 신청하지 않았으면 환불 대상이 없다") {
			val proposed: TeamMatch = TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now)

			proposed.failureRefunds() shouldBe emptyList()
		}

		it("성사(MATCHED)되어 전원 ACTIVE면 환불 대상이 없다") {
			val matched: TeamMatch = TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now).respond(10L, 100L).respond(20L, 200L)

			matched.failureRefunds() shouldBe emptyList()
		}
	}
})
