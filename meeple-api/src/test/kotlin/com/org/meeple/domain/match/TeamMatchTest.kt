package com.org.meeple.domain.match

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.core.match.command.domain.TeamMatch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
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
})
