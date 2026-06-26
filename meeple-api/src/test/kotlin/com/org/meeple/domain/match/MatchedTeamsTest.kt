package com.org.meeple.domain.match

import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.core.match.command.domain.MatchedTeam
import com.org.meeple.core.match.command.domain.MatchedTeams
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [MatchedTeams] 일급 컬렉션 유닛 테스트.
 * 참가 팀 일괄 비활성화를 검증한다.
 */
class MatchedTeamsTest : DescribeSpec({

	describe("deactivateAll") {
		it("모든 참가 팀을 DEACTIVE로 전이한 새 컬렉션을 돌려준다") {
			val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L))

			val deactivated: MatchedTeams = matchedTeams.deactivateAll()

			deactivated.values.all { it.status == MatchedTeamStatus.DEACTIVE } shouldBe true
			// 원본 불변
			matchedTeams.values.all { it.status == MatchedTeamStatus.WAITING } shouldBe true
		}
	}

	describe("apply - 특정 팀 신청") {
		it("주어진 teamId 팀만 APPLY로 전이하고 나머지는 그대로다") {
			val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L))

			val applied: MatchedTeams = matchedTeams.apply(10L)

			applied.values.first { it.teamId == 10L }.status shouldBe MatchedTeamStatus.APPLY
			applied.values.first { it.teamId == 20L }.status shouldBe MatchedTeamStatus.WAITING
			// 원본 불변
			matchedTeams.values.all { it.status == MatchedTeamStatus.WAITING } shouldBe true
		}
	}

	describe("allApplied / anyApplied") {
		it("전원 신청이면 allApplied=true, 일부면 anyApplied만 true") {
			val none: MatchedTeams = MatchedTeams.of(listOf(10L, 20L))
			none.anyApplied() shouldBe false
			none.allApplied() shouldBe false

			val partial: MatchedTeams = none.apply(10L)
			partial.anyApplied() shouldBe true
			partial.allApplied() shouldBe false

			val all: MatchedTeams = partial.apply(20L)
			all.allApplied() shouldBe true
		}
	}

	describe("activateAll") {
		it("모든 참가 팀을 ACTIVE로 승격한다") {
			val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L))

			val activated: MatchedTeams = matchedTeams.activateAll()

			activated.values.all { it.status == MatchedTeamStatus.ACTIVE } shouldBe true
		}
	}

	describe("isParticipant") {
		it("teamId가 참가 팀이면 true, 아니면 false") {
			val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L))

			matchedTeams.isParticipant(10L) shouldBe true
			matchedTeams.isParticipant(99L) shouldBe false
		}
	}

	describe("leave - 특정 팀 종료(비활성+soft delete)") {
		it("주어진 teamId 팀만 DEACTIVE + deletedAt을 채우고 나머지는 그대로다") {
			val now: LocalDateTime = LocalDateTime.of(2026, 6, 27, 12, 0)
			val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L)).activateAll()

			val left: MatchedTeams = matchedTeams.leave(10L, now)

			val ten: MatchedTeam = left.values.first { it.teamId == 10L }
			ten.status shouldBe MatchedTeamStatus.DEACTIVE
			ten.deletedAt shouldBe now
			val twenty: MatchedTeam = left.values.first { it.teamId == 20L }
			twenty.status shouldBe MatchedTeamStatus.ACTIVE
			twenty.deletedAt shouldBe null
		}
	}

	describe("isLastActiveTeam / allDeactivated") {
		it("상대 팀이 활성이면 isLastActiveTeam=false, allDeactivated=false") {
			val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L)).activateAll()

			matchedTeams.isLastActiveTeam(10L) shouldBe false
			matchedTeams.allDeactivated() shouldBe false
		}

		it("상대 팀이 이미 DEACTIVE면 isLastActiveTeam=true, 내가 나가면 allDeactivated=true") {
			val now: LocalDateTime = LocalDateTime.of(2026, 6, 27, 12, 0)
			val onlyTenActive: MatchedTeams = MatchedTeams.of(listOf(10L, 20L)).activateAll().leave(20L, now)

			onlyTenActive.isLastActiveTeam(10L) shouldBe true
			onlyTenActive.allDeactivated() shouldBe false
			onlyTenActive.leave(10L, now).allDeactivated() shouldBe true
		}
	}
})
