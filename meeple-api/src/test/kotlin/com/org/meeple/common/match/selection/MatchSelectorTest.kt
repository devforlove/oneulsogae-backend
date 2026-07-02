package com.org.meeple.common.match.selection

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import kotlin.random.Random

private data class Cand(
	override val userId: Long,
	override val regionId: Long,
	override val lastLoginAt: LocalDateTime,
	override val companyName: String? = null,
	override val refuseSameCompanyIntro: Boolean = true,
) : ScoringCandidate

class MatchSelectorTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 7, 1, 12, 0)
	val loginAfter: LocalDateTime = now.minusWeeks(2)
	// 같은 지역(rank 0)·최근 로그인일수록 점수가 높다. 이상형은 프로필 null → 중립(1.0).
	val near = Cand(userId = 1L, regionId = 10L, lastLoginAt = now)
	val far = Cand(userId = 2L, regionId = 99L, lastLoginAt = loginAfter)
	val rank: Map<Long, Int> = mapOf(10L to 0, 99L to 1)

	fun selectBest(
		candidates: List<Cand>,
		targetCompanyName: String? = null,
		targetRefusesSameCompanyIntro: Boolean = true,
		isExcluded: (Cand) -> Boolean = { false },
	): Cand? =
		MatchSelector.selectBest(
			targetProfile = null,
			targetCompanyName = targetCompanyName,
			targetRefusesSameCompanyIntro = targetRefusesSameCompanyIntro,
			candidates = candidates,
			profileOf = { null },
			regionRankByRegionId = rank,
			regionCount = 2,
			now = now,
			loginAfter = loginAfter,
			random = Random(0),
			isExcluded = isExcluded,
		)

	describe("selectBest") {
		it("가장 높은 점수(가까운 지역·최근)의 후보를 고른다") {
			selectBest(listOf(far, near))?.userId shouldBe 1L
		}

		it("최고점 후보가 제외되면 다음 후보를 고른다") {
			selectBest(listOf(far, near), isExcluded = { c: Cand -> c.userId == 1L })?.userId shouldBe 2L
		}

		it("모든 후보가 제외되면 null") {
			selectBest(listOf(far, near), isExcluded = { true }).shouldBeNull()
		}
	}

	describe("selectBest - 같은 회사 소개 차단") {
		val sameCompanyNear = near.copy(companyName = "미플컴퍼니", refuseSameCompanyIntro = false)
		val otherCompanyFar = far.copy(companyName = "다른회사")

		it("대상이 거부하면 같은 회사 후보를 제외하고 다음 후보를 고른다") {
			val picked: Cand? = selectBest(
				candidates = listOf(sameCompanyNear, otherCompanyFar),
				targetCompanyName = "미플컴퍼니",
				targetRefusesSameCompanyIntro = true,
			)
			picked?.userId shouldBe 2L
		}

		it("대상이 거부하지 않아도 같은 회사 후보가 거부하면 제외된다(양방향)") {
			val refusingSameCompany = near.copy(companyName = "미플컴퍼니", refuseSameCompanyIntro = true)
			val picked: Cand? = selectBest(
				candidates = listOf(refusingSameCompany),
				targetCompanyName = "미플컴퍼니",
				targetRefusesSameCompanyIntro = false,
			)
			picked.shouldBeNull()
		}

		it("같은 회사라도 양쪽 모두 거부를 해제했으면 선택된다") {
			val picked: Cand? = selectBest(
				candidates = listOf(sameCompanyNear),
				targetCompanyName = "미플컴퍼니",
				targetRefusesSameCompanyIntro = false,
			)
			picked?.userId shouldBe 1L
		}

		it("대상 회사가 미상(null)이면 차단 없이 최고점 후보를 고른다") {
			val picked: Cand? = selectBest(
				candidates = listOf(near.copy(companyName = "미플컴퍼니")),
				targetCompanyName = null,
				targetRefusesSameCompanyIntro = true,
			)
			picked?.userId shouldBe 1L
		}
	}
})
