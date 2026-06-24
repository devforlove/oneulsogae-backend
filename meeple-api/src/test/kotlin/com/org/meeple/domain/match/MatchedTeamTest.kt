package com.org.meeple.domain.match

import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.core.match.command.domain.MatchedTeam
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [MatchedTeam] 도메인 유닛 테스트.
 * 팀 매칭 참가 팀의 상태 전이(WAITING→APPLY→ACTIVE/DEACTIVE)를 검증한다.
 */
class MatchedTeamTest : DescribeSpec({

	fun waitingTeam(): MatchedTeam =
		MatchedTeam(teamMatchId = 1L, teamId = 10L)

	describe("초기 상태") {
		it("생성 직후에는 WAITING이다") {
			waitingTeam().status shouldBe MatchedTeamStatus.WAITING
		}
	}

	describe("apply") {
		it("이 팀을 신청(APPLY) 상태로 전이한다") {
			waitingTeam().apply().status shouldBe MatchedTeamStatus.APPLY
		}
	}

	describe("activate") {
		it("이 팀을 활성(ACTIVE) 상태로 전이한다") {
			waitingTeam().apply().activate().status shouldBe MatchedTeamStatus.ACTIVE
		}
	}

	describe("deactivate") {
		it("이 팀을 비활성(DEACTIVE) 상태로 전이한다") {
			waitingTeam().deactivate().status shouldBe MatchedTeamStatus.DEACTIVE
		}
	}
})
