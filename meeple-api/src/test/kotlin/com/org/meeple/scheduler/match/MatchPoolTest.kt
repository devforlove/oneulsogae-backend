package com.org.meeple.scheduler.match

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.command.domain.MatchPool
import com.org.meeple.scheduler.match.query.dto.MatchableUser
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class MatchPoolTest : DescribeSpec({

	val base: LocalDateTime = LocalDateTime.of(2026, 6, 23, 0, 0)
	fun user(id: Long, gender: Gender, regionId: Long, lastLoginAt: LocalDateTime): MatchableUser =
		MatchableUser(userId = id, gender = gender, regionId = regionId, lastLoginAt = lastLoginAt)

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

	describe("remove") {

		it("제거된 유저는 freshCandidates·contains에서 빠진다") {
			val target: MatchableUser = user(10L, Gender.FEMALE, 1L, base)
			val pool: MatchPool = MatchPool.of(listOf(target))

			pool.remove(target)

			pool.contains(target) shouldBe false
			pool.freshCandidates(Gender.FEMALE, 1L).shouldBeEmpty()
		}
	}
})
