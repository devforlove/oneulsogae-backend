package com.org.meeple.common.match

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import kotlin.random.Random

private data class Cand(
	override val userId: Long,
	override val regionId: Long,
	override val lastLoginAt: LocalDateTime,
) : ScoringCandidate

class MatchSelectorTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 7, 1, 12, 0)
	val loginAfter: LocalDateTime = now.minusWeeks(2)
	// 같은 지역(rank 0)·최근 로그인일수록 점수가 높다. 이상형은 프로필 null → 중립(1.0).
	val near = Cand(userId = 1L, regionId = 10L, lastLoginAt = now)
	val far = Cand(userId = 2L, regionId = 99L, lastLoginAt = loginAfter)
	val rank: Map<Long, Int> = mapOf(10L to 0, 99L to 1)

	describe("selectBest") {
		it("가장 높은 점수(가까운 지역·최근)의 후보를 고른다") {
			val picked = MatchSelector.selectBest(
				targetProfile = null,
				candidates = listOf(far, near),
				profileOf = { null },
				regionRankByRegionId = rank,
				regionCount = 2,
				now = now,
				loginAfter = loginAfter,
				random = Random(0),
				isExcluded = { false },
			)
			picked?.userId shouldBe 1L
		}

		it("최고점 후보가 제외되면 다음 후보를 고른다") {
			val picked = MatchSelector.selectBest(
				targetProfile = null,
				candidates = listOf(far, near),
				profileOf = { null },
				regionRankByRegionId = rank,
				regionCount = 2,
				now = now,
				loginAfter = loginAfter,
				random = Random(0),
				isExcluded = { c -> c.userId == 1L },
			)
			picked?.userId shouldBe 2L
		}

		it("모든 후보가 제외되면 null") {
			val picked = MatchSelector.selectBest(
				targetProfile = null,
				candidates = listOf(far, near),
				profileOf = { null },
				regionRankByRegionId = rank,
				regionCount = 2,
				now = now,
				loginAfter = loginAfter,
				random = Random(0),
				isExcluded = { true },
			)
			picked.shouldBeNull()
		}
	}
})
