package com.org.meeple.domain.match

import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.core.match.command.domain.MatchedTeams
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [MatchedTeams] 일급 컬렉션 유닛 테스트.
 * 참가 팀 일괄 비활성화와 상대 팀 식별을 검증한다.
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

	describe("opponentTeamIdOf") {
		it("주어진 teamId가 아닌 상대 팀의 teamId를 돌려준다") {
			val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L))

			matchedTeams.opponentTeamIdOf(10L) shouldBe 20L
			matchedTeams.opponentTeamIdOf(20L) shouldBe 10L
		}
	}
})
