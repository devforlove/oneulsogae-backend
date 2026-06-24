package com.org.meeple.domain.match

import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.core.match.command.domain.MatchedTeams
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

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
})
