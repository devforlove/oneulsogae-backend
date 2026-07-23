package com.org.oneulsogae.domain.match

import com.org.oneulsogae.common.match.MatchedTeamStatus
import com.org.oneulsogae.core.teammatch.command.domain.MatchedTeam
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

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
		it("이 팀을 신청(APPLY) 상태로 전이하고 신청자와 지불액을 기록한다") {
			val applied: MatchedTeam = waitingTeam().apply(100L, paidInitAmount = 20)

			applied.status shouldBe MatchedTeamStatus.APPLY
			applied.applicantUserId shouldBe 100L
			applied.paidInitAmount shouldBe 20
		}
	}

	describe("activate") {
		it("이 팀을 활성(ACTIVE) 상태로 전이한다") {
			waitingTeam().apply(100L, paidInitAmount = 40).activate().status shouldBe MatchedTeamStatus.ACTIVE
		}
	}

	describe("deactivate") {
		it("이 팀을 비활성(DEACTIVE) 상태로 전이하되 소프트 삭제는 하지 않는다") {
			val deactivated: MatchedTeam = waitingTeam().deactivate()

			deactivated.status shouldBe MatchedTeamStatus.DEACTIVE
			deactivated.deletedAt shouldBe null
		}
	}

	describe("delete") {
		it("이 팀을 비활성(DEACTIVE) + 소프트 삭제(deletedAt)한다") {
			val now: LocalDateTime = LocalDateTime.of(2026, 6, 27, 12, 0)

			val deleted: MatchedTeam = waitingTeam().delete(now)

			deleted.status shouldBe MatchedTeamStatus.DEACTIVE
			deleted.deletedAt shouldBe now
		}
	}

	describe("isDeactivated") {
		it("status가 DEACTIVE일 때만 true다") {
			waitingTeam().isDeactivated shouldBe false
			waitingTeam().deactivate().isDeactivated shouldBe true
		}
	}
})
