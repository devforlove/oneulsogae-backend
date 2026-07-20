package com.org.oneulsogae.scheduler.solomatch

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.scheduler.solomatch.command.domain.MatchPool
import com.org.oneulsogae.scheduler.solomatch.query.dto.MatchableUser
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class MatchPoolTest : DescribeSpec({

	val base: LocalDateTime = LocalDateTime.of(2026, 6, 23, 0, 0)
	fun user(id: Long, gender: Gender, regionId: Long, lastLoginAt: LocalDateTime): MatchableUser =
		MatchableUser(userId = id, gender = gender, regionId = regionId, lastLoginAt = lastLoginAt, companyName = null, refuseSameCompanyIntro = true)

	describe("freshCandidates") {

		it("같은 (성별, 지역) 버킷의 가용 후보를 최근 로그인순으로 돌려준다") {
			val older: MatchableUser = user(10L, Gender.FEMALE, 1L, base.minusDays(2))
			val newer: MatchableUser = user(11L, Gender.FEMALE, 1L, base.minusDays(1))
			val pool: MatchPool = MatchPool.of(listOf(older, newer))

			pool.freshCandidates(Gender.FEMALE, 1L) shouldBe listOf(newer, older)
		}

		it("다른 성별/지역은 섞이지 않는다") {
			val femaleRegion1: MatchableUser = user(10L, Gender.FEMALE, 1L, base)
			val femaleRegion2: MatchableUser = user(11L, Gender.FEMALE, 2L, base)
			val maleRegion1: MatchableUser = user(12L, Gender.MALE, 1L, base)
			val pool: MatchPool = MatchPool.of(listOf(femaleRegion1, femaleRegion2, maleRegion1))

			pool.freshCandidates(Gender.FEMALE, 1L) shouldBe listOf(femaleRegion1)
		}
	}

	describe("regionsWith") {

		it("그 성별 후보가 있는 지역만 돌려준다 (다른 성별·없는 지역은 제외)") {
			val femaleRegion1: MatchableUser = user(10L, Gender.FEMALE, 1L, base)
			val femaleRegion2: MatchableUser = user(11L, Gender.FEMALE, 2L, base)
			val maleRegion3: MatchableUser = user(12L, Gender.MALE, 3L, base)
			val pool: MatchPool = MatchPool.of(listOf(femaleRegion1, femaleRegion2, maleRegion3))

			pool.regionsWith(Gender.FEMALE) shouldContainExactlyInAnyOrder setOf(1L, 2L)
			pool.regionsWith(Gender.MALE) shouldContainExactlyInAnyOrder setOf(3L)
		}

		it("그 성별 후보가 없으면 빈 집합을 돌려준다") {
			val pool: MatchPool = MatchPool.of(listOf(user(10L, Gender.FEMALE, 1L, base)))

			pool.regionsWith(Gender.MALE).shouldBeEmpty()
		}
	}

	describe("remove") {

		it("제거된 유저는 freshCandidates·contains에서 빠진다") {
			val target: MatchableUser = user(10L, Gender.FEMALE, 1L, base)
			val pool: MatchPool = MatchPool.of(listOf(target))

			pool.remove(target)

			pool.contains(target) shouldBe false
			pool.freshCandidates(Gender.FEMALE, 1L).shouldBeEmpty()
		}
	}

	describe("remainingUserIds") {

		it("아직 제거되지 않은(짝지어지지 않은) 유저 전체를 돌려준다") {
			val matched: MatchableUser = user(10L, Gender.FEMALE, 1L, base)
			val unmatchedA: MatchableUser = user(11L, Gender.FEMALE, 1L, base)
			val unmatchedB: MatchableUser = user(12L, Gender.MALE, 2L, base)
			val pool: MatchPool = MatchPool.of(listOf(matched, unmatchedA, unmatchedB))

			pool.remove(matched)

			pool.remainingUserIds() shouldContainExactlyInAnyOrder setOf(11L, 12L)
		}

		it("모두 제거되면 빈 집합을 돌려준다") {
			val target: MatchableUser = user(10L, Gender.FEMALE, 1L, base)
			val pool: MatchPool = MatchPool.of(listOf(target))

			pool.remove(target)

			pool.remainingUserIds().shouldBeEmpty()
		}
	}

	describe("availableCandidates") {

		it("지역과 무관하게 해당 성별의 가용 후보를 모두 돌려준다") {
			val femaleRegion1: MatchableUser = user(1L, Gender.FEMALE, 10L, base)
			val femaleRegion2: MatchableUser = user(2L, Gender.FEMALE, 20L, base)
			val maleRegion1: MatchableUser = user(3L, Gender.MALE, 10L, base)
			val pool: MatchPool = MatchPool.of(listOf(femaleRegion1, femaleRegion2, maleRegion1))

			pool.availableCandidates(Gender.FEMALE).map { it.userId }.sorted() shouldBe listOf(1L, 2L)
		}

		it("remove된 후보는 제외된다") {
			val female1: MatchableUser = user(1L, Gender.FEMALE, 10L, base)
			val female2: MatchableUser = user(2L, Gender.FEMALE, 20L, base)
			val pool: MatchPool = MatchPool.of(listOf(female1, female2))

			pool.remove(female1)

			pool.availableCandidates(Gender.FEMALE).map { it.userId } shouldBe listOf(2L)
		}
	}
})
