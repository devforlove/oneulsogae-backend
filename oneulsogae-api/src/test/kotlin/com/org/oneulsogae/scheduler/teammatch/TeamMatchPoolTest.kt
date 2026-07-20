package com.org.oneulsogae.scheduler.teammatch

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.scheduler.teammatch.command.domain.TeamMatchPool
import com.org.oneulsogae.scheduler.teammatch.query.dto.MatchableTeam
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import java.time.LocalDateTime

class TeamMatchPoolTest : DescribeSpec({

	val base: LocalDateTime = LocalDateTime.of(2026, 6, 23, 0, 0)
	fun team(teamId: Long, gender: Gender, regionId: Long, lastLoginAt: LocalDateTime): MatchableTeam =
		MatchableTeam(teamId = teamId, gender = gender, regionId = regionId, lastLoginAt = lastLoginAt)

	describe("remainingTeamIds") {

		it("아직 제거되지 않은(짝지어지지 않은) 팀 전체를 돌려준다") {
			val matched: MatchableTeam = team(70L, Gender.FEMALE, 1L, base)
			val unmatchedA: MatchableTeam = team(71L, Gender.FEMALE, 1L, base)
			val unmatchedB: MatchableTeam = team(72L, Gender.MALE, 2L, base)
			val pool: TeamMatchPool = TeamMatchPool.of(listOf(matched, unmatchedA, unmatchedB))

			pool.remove(matched)

			pool.remainingTeamIds() shouldContainExactlyInAnyOrder setOf(71L, 72L)
		}

		it("모두 제거되면 빈 집합을 돌려준다") {
			val target: MatchableTeam = team(70L, Gender.FEMALE, 1L, base)
			val pool: TeamMatchPool = TeamMatchPool.of(listOf(target))

			pool.remove(target)

			pool.remainingTeamIds().shouldBeEmpty()
		}
	}
})
