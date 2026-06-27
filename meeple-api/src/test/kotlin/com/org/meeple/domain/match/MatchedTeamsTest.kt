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

	describe("deactivate - 특정 팀 비활성(소프트 삭제 안 함)") {
		it("주어진 teamId 팀만 DEACTIVE로 전이하고 deletedAt은 채우지 않으며 나머지는 그대로다") {
			val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L)).activateAll()

			val left: MatchedTeams = matchedTeams.deactivate(10L)

			val ten: MatchedTeam = left.values.first { it.teamId == 10L }
			ten.status shouldBe MatchedTeamStatus.DEACTIVE
			ten.deletedAt shouldBe null
			val twenty: MatchedTeam = left.values.first { it.teamId == 20L }
			twenty.status shouldBe MatchedTeamStatus.ACTIVE
			twenty.deletedAt shouldBe null
		}
	}

	describe("delete - 전원 소프트 삭제") {
		it("모든 참가 팀을 DEACTIVE + deletedAt으로 제거한다") {
			val now: LocalDateTime = LocalDateTime.of(2026, 6, 27, 12, 0)
			val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L)).activateAll()

			val deleted: MatchedTeams = matchedTeams.delete(now)

			deleted.values.all { it.status == MatchedTeamStatus.DEACTIVE } shouldBe true
			deleted.values.all { it.deletedAt == now } shouldBe true
		}
	}

	describe("find") {
		it("참가 팀이면 그 MatchedTeam을, 아니면 null을 돌려준다") {
			val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L))

			matchedTeams.find(10L)?.teamId shouldBe 10L
			matchedTeams.find(99L) shouldBe null
		}
	}

	describe("isLastActiveTeam / allDeactivated") {
		it("상대 팀이 활성이면 isLastActiveTeam=false, allDeactivated=false") {
			val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L)).activateAll()

			matchedTeams.isLastActiveTeam(10L) shouldBe false
			matchedTeams.allDeactivated() shouldBe false
		}

		it("상대 팀이 이미 DEACTIVE면 isLastActiveTeam=true, 내가 나가면 allDeactivated=true") {
			val onlyTenActive: MatchedTeams = MatchedTeams.of(listOf(10L, 20L)).activateAll().deactivate(20L)

			onlyTenActive.isLastActiveTeam(10L) shouldBe true
			onlyTenActive.allDeactivated() shouldBe false
			onlyTenActive.deactivate(10L).allDeactivated() shouldBe true
		}
	}
})
