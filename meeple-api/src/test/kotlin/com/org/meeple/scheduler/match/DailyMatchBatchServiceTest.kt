package com.org.meeple.scheduler.match

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.command.application.DailyMatchBatchService
import com.org.meeple.scheduler.match.command.application.port.out.RegionProximityPort
import com.org.meeple.scheduler.match.command.application.port.out.SaveMatchRecordPort
import com.org.meeple.scheduler.match.command.application.port.out.TimeGenerator
import com.org.meeple.scheduler.match.command.domain.MatchBatchResult
import com.org.meeple.scheduler.match.query.dao.GetMatchRecordDao
import com.org.meeple.scheduler.match.query.dao.GetMatchableUserDao
import com.org.meeple.scheduler.match.query.dto.MatchableUser
import com.org.meeple.scheduler.match.query.dto.MatchedUserIds
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

class DailyMatchBatchServiceTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 6, 23, 12, 0)
	fun user(id: Long, gender: Gender, regionId: Long): MatchableUser =
		MatchableUser(userId = id, gender = gender, regionId = regionId, lastLoginAt = now.minusDays(1))

	describe("run") {

		it("가까운 지역의 반대 성별 후보와 짝짓는다") {
			val male: MatchableUser = user(1L, Gender.MALE, 1L)
			val nearFemale: MatchableUser = user(2L, Gender.FEMALE, 1L)
			val farFemale: MatchableUser = user(3L, Gender.FEMALE, 2L)
			val saved = BatchFakeSaveMatchRecordPort()
			val service = DailyMatchBatchService(
				BatchFakeGetMatchableUserDao(listOf(male, nearFemale, farFemale)),
				BatchFakeGetMatchRecordDao(),
				saved,
				BatchFakeRegionProximityPort(mapOf(1L to listOf(1L, 2L))),
				BatchFakeTimeGenerator(now),
			)

			val result: MatchBatchResult = service.run()

			result.recommended shouldBe 1
			saved.pairs shouldContainExactly listOf(1L to 2L)   // 가까운 지역(1)의 여성과
		}

		it("재소개 이력이 있는 후보는 건너뛴다") {
			val male: MatchableUser = user(1L, Gender.MALE, 1L)
			val introduced: MatchableUser = user(2L, Gender.FEMALE, 1L)
			val fresh: MatchableUser = user(3L, Gender.FEMALE, 1L)
			val saved = BatchFakeSaveMatchRecordPort()
			val service = DailyMatchBatchService(
				BatchFakeGetMatchableUserDao(listOf(male, introduced, fresh)),
				BatchFakeGetMatchRecordDao(existingPairs = setOf(1L to 2L)),
				saved,
				BatchFakeRegionProximityPort(mapOf(1L to listOf(1L))),
				BatchFakeTimeGenerator(now),
			)

			service.run()

			saved.pairs shouldContainExactly listOf(1L to 3L)
		}

		it("성사·오늘매칭 유저는 대상·후보에서 제외된다") {
			val male: MatchableUser = user(1L, Gender.MALE, 1L)
			val matchedFemale: MatchableUser = user(2L, Gender.FEMALE, 1L)   // 성사 상태
			val todayFemale: MatchableUser = user(3L, Gender.FEMALE, 1L)     // 오늘 매칭됨
			val saved = BatchFakeSaveMatchRecordPort()
			val service = DailyMatchBatchService(
				BatchFakeGetMatchableUserDao(listOf(male, matchedFemale, todayFemale)),
				BatchFakeGetMatchRecordDao(matchedUserIds = setOf(2L), introducedTodayUserIds = setOf(3L)),
				saved,
				BatchFakeRegionProximityPort(mapOf(1L to listOf(1L))),
				BatchFakeTimeGenerator(now),
			)

			val result: MatchBatchResult = service.run()

			result.recommended shouldBe 0   // 남은 후보가 없어 male은 짝을 못 찾음
		}

		it("같은 실행에서 한 사람을 두 번 짝짓지 않는다") {
			val male1: MatchableUser = user(1L, Gender.MALE, 1L)
			val male2: MatchableUser = user(2L, Gender.MALE, 1L)
			val female: MatchableUser = user(3L, Gender.FEMALE, 1L)
			val saved = BatchFakeSaveMatchRecordPort()
			val service = DailyMatchBatchService(
				BatchFakeGetMatchableUserDao(listOf(male1, male2, female)),
				BatchFakeGetMatchRecordDao(),
				saved,
				BatchFakeRegionProximityPort(mapOf(1L to listOf(1L))),
				BatchFakeTimeGenerator(now),
			)

			val result: MatchBatchResult = service.run()

			result.recommended shouldBe 1   // female은 한 번만 짝지어짐
		}
	}
})

private class BatchFakeGetMatchableUserDao(private val users: List<MatchableUser>) : GetMatchableUserDao {
	override fun findMatchableUsers(loginAfter: LocalDateTime): List<MatchableUser> = users
}

private class BatchFakeGetMatchRecordDao(
	private val existingPairs: Set<Pair<Long, Long>> = emptySet(),
	private val matchedUserIds: Set<Long> = emptySet(),
	private val introducedTodayUserIds: Set<Long> = emptySet(),
) : GetMatchRecordDao {
	override fun existsByPair(userIdA: Long, userIdB: Long): Boolean {
		val key: Pair<Long, Long> = listOf(userIdA, userIdB).sorted().let { it[0] to it[1] }
		return existingPairs.any { listOf(it.first, it.second).sorted().let { s -> s[0] to s[1] } == key }
	}
	override fun findMatchedUserIds(): MatchedUserIds = MatchedUserIds(matchedUserIds)
	override fun findUserIdsIntroducedOn(date: LocalDate): Set<Long> = introducedTodayUserIds
}

private class BatchFakeSaveMatchRecordPort : SaveMatchRecordPort {
	val pairs: MutableList<Pair<Long, Long>> = mutableListOf()
	override fun saveProposedMatch(requesterId: Long, requesterGender: Gender, partnerId: Long, now: LocalDateTime) {
		pairs.add(requesterId to partnerId)
	}
}

private class BatchFakeRegionProximityPort(private val nearby: Map<Long, List<Long>>) : RegionProximityPort {
	override fun refresh() = Unit
	override fun nearbyRegionIds(regionId: Long): List<Long> = nearby[regionId] ?: emptyList()
}

private class BatchFakeTimeGenerator(private val fixed: LocalDateTime) : TimeGenerator {
	override fun now(): LocalDateTime = fixed
}
