package com.org.meeple.scheduler.match

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.command.application.MatchIntroducer
import com.org.meeple.scheduler.match.command.application.port.out.MatchPoolPort
import com.org.meeple.scheduler.match.command.application.port.out.RegionProximityPort
import com.org.meeple.scheduler.match.command.application.port.out.SaveMatchRecordPort
import com.org.meeple.scheduler.match.query.dao.GetMatchRecordDao
import com.org.meeple.scheduler.match.query.dto.MatchedUserIds
import io.kotest.core.spec.style.DescribeSpec
import java.time.LocalDate
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class MatchIntroducerTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 6, 23, 0, 0)

	describe("introduce") {

		context("가까운 지역과 먼 지역에 모두 후보가 있으면") {
			it("가까운 지역 후보를 먼저 소개한다") {
				val pools = FakeMatchPoolPort(
					mutableMapOf(
						(Gender.FEMALE to 1L) to mutableListOf(100L), // 같은 지역
						(Gender.FEMALE to 2L) to mutableListOf(200L), // 먼 지역
					),
				)
				val records = FakeGetMatchRecordDao(existingPairs = emptySet())
				val saved = FakeSaveMatchRecordPort()
				val proximity = FakeRegionProximityPort(mapOf(1L to listOf(1L, 2L)))
				val introducer = MatchIntroducer(records, saved, pools, proximity)

				val partnerId: Long? = introducer.introduce(
					targetId = 10L, gender = Gender.MALE, regionId = 1L,
					regionByUserId = mapOf(100L to 1L), now = now,
				)

				partnerId shouldBe 100L
				saved.saved shouldBe listOf(Triple(10L, Gender.MALE, 100L))
			}
		}

		context("가까운 지역 후보가 모두 재소개 이력이면") {
			it("다음 가까운 지역에서 신선 후보를 찾는다") {
				val pools = FakeMatchPoolPort(
					mutableMapOf(
						(Gender.FEMALE to 1L) to mutableListOf(100L),
						(Gender.FEMALE to 2L) to mutableListOf(200L),
					),
				)
				// 남성 10L 과 100L 은 이미 소개됨 (정렬 키: "10-100")
				val records = FakeGetMatchRecordDao(existingPairs = setOf(10L to 100L))
				val saved = FakeSaveMatchRecordPort()
				val proximity = FakeRegionProximityPort(mapOf(1L to listOf(1L, 2L)))
				val introducer = MatchIntroducer(records, saved, pools, proximity)

				val partnerId: Long? = introducer.introduce(
					targetId = 10L, gender = Gender.MALE, regionId = 1L,
					regionByUserId = mapOf(200L to 2L), now = now,
				)

				partnerId shouldBe 200L
				// 이력 후보 100L 은 같은 지역 풀로 되돌아가 있다
				pools.peek(Gender.FEMALE, 1L) shouldBe listOf(100L)
			}
		}

		context("모든 근접 지역 풀이 비어 있으면") {
			it("소개하지 못하고 null을 반환한다") {
				val pools = FakeMatchPoolPort(mutableMapOf())
				val records = FakeGetMatchRecordDao(existingPairs = emptySet())
				val saved = FakeSaveMatchRecordPort()
				val proximity = FakeRegionProximityPort(mapOf(1L to listOf(1L, 2L)))
				val introducer = MatchIntroducer(records, saved, pools, proximity)

				introducer.introduce(
					targetId = 10L, gender = Gender.MALE, regionId = 1L,
					regionByUserId = emptyMap(), now = now,
				).shouldBeNull()
			}
		}
	}
})

private class FakeMatchPoolPort(
	private val pools: MutableMap<Pair<Gender, Long>, MutableList<Long>>,
) : MatchPoolPort {
	override fun pop(gender: Gender, regionId: Long): Long? =
		pools[gender to regionId]?.removeFirstOrNull()

	override fun pushBack(gender: Gender, regionId: Long, userIds: List<Long>) {
		if (userIds.isEmpty()) return
		pools.getOrPut(gender to regionId) { mutableListOf() }.addAll(userIds)
	}

	override fun remove(gender: Gender, regionId: Long, userId: Long) {
		pools[gender to regionId]?.remove(userId)
	}

	fun peek(gender: Gender, regionId: Long): List<Long> =
		pools[gender to regionId]?.toList() ?: emptyList()
}

private class FakeGetMatchRecordDao(
	private val existingPairs: Set<Pair<Long, Long>>,
) : GetMatchRecordDao {
	override fun existsByPair(userIdA: Long, userIdB: Long): Boolean {
		val key: Pair<Long, Long> = listOf(userIdA, userIdB).sorted().let { it[0] to it[1] }
		return existingPairs.any { listOf(it.first, it.second).sorted().let { s -> s[0] to s[1] } == key }
	}

	override fun findMatchedUserIds(): MatchedUserIds = MatchedUserIds(emptySet())
	override fun findUserIdsIntroducedOn(date: LocalDate): Set<Long> = emptySet()
}

private class FakeSaveMatchRecordPort : SaveMatchRecordPort {
	val saved: MutableList<Triple<Long, Gender, Long>> = mutableListOf()
	override fun saveProposedMatch(requesterId: Long, requesterGender: Gender, partnerId: Long, now: LocalDateTime) {
		saved.add(Triple(requesterId, requesterGender, partnerId))
	}
}

private class FakeRegionProximityPort(
	private val nearby: Map<Long, List<Long>>,
) : RegionProximityPort {
	override fun refresh() = Unit
	override fun nearbyRegionIds(regionId: Long): List<Long> = nearby[regionId] ?: emptyList()
}
