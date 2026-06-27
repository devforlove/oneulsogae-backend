package com.org.meeple.scheduler.match

import com.org.meeple.scheduler.match.query.dto.PreviouslyMatchedTeams
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PreviouslyMatchedTeamsTest : DescribeSpec({

    describe("opponentTeamIdsOf") {
        it("유저에 매핑된 상대 team_id 집합을 돌려준다") {
            val previouslyMatched = PreviouslyMatchedTeams(mapOf(1L to setOf(100L, 200L)))
            previouslyMatched.opponentTeamIdsOf(1L) shouldBe setOf(100L, 200L)
        }

        it("이력이 없는 유저는 빈 집합을 돌려준다") {
            val previouslyMatched = PreviouslyMatchedTeams(mapOf(1L to setOf(100L)))
            previouslyMatched.opponentTeamIdsOf(2L) shouldBe emptySet()
        }
    }
})
