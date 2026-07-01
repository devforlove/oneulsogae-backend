package com.org.meeple.scheduler.solomatch

import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.matching.MatchScoringProfile
import com.org.meeple.scheduler.common.command.application.port.out.NoIntroductionAlarmPort
import com.org.meeple.scheduler.common.command.application.port.out.RegionProximityPort
import com.org.meeple.scheduler.common.command.application.port.out.TimeGenerator
import com.org.meeple.scheduler.solomatch.command.application.SoloMatchBatchService
import com.org.meeple.scheduler.solomatch.command.application.port.out.SaveMatchRecordPort
import com.org.meeple.scheduler.solomatch.query.dao.GetMatchRecordDao
import com.org.meeple.scheduler.solomatch.query.dao.GetMatchScoringProfileDao
import com.org.meeple.scheduler.solomatch.query.dao.GetMatchableUserDao
import com.org.meeple.scheduler.solomatch.query.dto.MatchableUser
import com.org.meeple.scheduler.solomatch.query.dto.MatchedUserIds
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * [SoloMatchBatchService] 유닛 테스트. 이상형은 필터가 아니라 우선순위임을 중심으로 검증한다.
 * 모든 협력자는 페이크로 대체하고, 시각·랜덤을 고정해 결정적으로 만든다.
 */
class SoloMatchBatchServiceTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 7, 1, 12, 0)

	// saves: (requesterId, partnerId) 소개 기록. existsPairs: 이미 재소개 이력 있는 쌍(정렬된 Pair).
	fun service(
		matchables: List<MatchableUser>,
		profiles: Map<Long, MatchScoringProfile>,
		existsPairs: Set<Pair<Long, Long>>,
		saves: MutableList<Pair<Long, Long>>,
	): SoloMatchBatchService =
		SoloMatchBatchService(
			getMatchableUserDao = object : GetMatchableUserDao {
				override fun findMatchableUsers(loginAfter: LocalDateTime): List<MatchableUser> = matchables
			},
			getMatchScoringProfileDao = object : GetMatchScoringProfileDao {
				override fun load(userIds: Set<Long>, today: LocalDate): Map<Long, MatchScoringProfile> =
					profiles.filterKeys { it in userIds }
			},
			getMatchRecordDao = object : GetMatchRecordDao {
				override fun existsByPair(userIdA: Long, userIdB: Long): Boolean =
					(minOf(userIdA, userIdB) to maxOf(userIdA, userIdB)) in existsPairs
				override fun findMatchedUserIds(): MatchedUserIds = MatchedUserIds(emptySet())
				override fun findUserIdsIntroducedOn(date: LocalDate): Set<Long> = emptySet()
			},
			saveMatchRecordPort = object : SaveMatchRecordPort {
				override fun saveProposedMatch(requesterId: Long, requesterGender: Gender, partnerId: Long, now: LocalDateTime) {
					saves.add(requesterId to partnerId)
				}
			},
			regionProximityPort = object : RegionProximityPort {
				override fun refresh() {}
				override fun nearbyRegionIds(regionId: Long): List<Long> = listOf(regionId)
			},
			timeGenerator = object : TimeGenerator {
				override fun now(): LocalDateTime = now
			},
			noIntroductionAlarmPort = object : NoIntroductionAlarmPort {
				override fun notifySoloUnmatched(userIds: Collection<Long>, now: LocalDateTime) {}
				override fun notifyTeamUnmatched(teamIds: Collection<Long>, now: LocalDateTime) {}
			},
			random = Random(0),
		)

	// 지정한 이상형/속성만 채운 프로필.
	fun profile(userId: Long, maritalStatus: MaritalStatus? = null, idealMaritalStatus: MaritalStatus? = null): MatchScoringProfile =
		MatchScoringProfile(
			userId = userId, age = null, height = null, maritalStatus = maritalStatus,
			smokingStatus = null, drinkingStatus = null, religion = null,
			idealAgeMin = null, idealAgeMax = null, idealHeightMin = null, idealHeightMax = null,
			idealMaritalStatus = idealMaritalStatus, idealSmokingStatus = null, idealDrinkingStatus = null, idealReligion = null,
		)

	fun male(userId: Long, lastLoginAt: LocalDateTime): MatchableUser = MatchableUser(userId, Gender.MALE, 1L, lastLoginAt)
	fun female(userId: Long, lastLoginAt: LocalDateTime): MatchableUser = MatchableUser(userId, Gender.FEMALE, 1L, lastLoginAt)

	describe("run - 이상형 우선순위") {

		it("거리·최근이 같으면 이상형이 더 맞는 후보를 우선 소개한다") {
			// 대상 1001(남, 가장 최근)의 이상형: 미혼. 1002는 미혼(부합), 1003은 돌싱(불충족).
			val matchables: List<MatchableUser> = listOf(
				male(1001L, now),
				female(1002L, now.minusMinutes(1)),
				female(1003L, now.minusMinutes(1)),
			)
			val profiles: Map<Long, MatchScoringProfile> = mapOf(
				1001L to profile(1001L, idealMaritalStatus = MaritalStatus.SINGLE),
				1002L to profile(1002L, maritalStatus = MaritalStatus.SINGLE),
				1003L to profile(1003L, maritalStatus = MaritalStatus.DIVORCED),
			)
			val saves: MutableList<Pair<Long, Long>> = mutableListOf()

			val result = service(matchables, profiles, existsPairs = emptySet(), saves).run()

			result.recommended shouldBe 1
			saves shouldContainExactlyInAnyOrder listOf(1001L to 1002L)
		}

		it("이상형이 전혀 안 맞아도 다른 후보가 없으면 소개한다(필터 아님)") {
			// 대상 1001 이상형: 미혼. 유일 후보 1003은 돌싱(불충족)이지만 그래도 소개돼야 한다.
			val matchables: List<MatchableUser> = listOf(male(1001L, now), female(1003L, now.minusMinutes(1)))
			val profiles: Map<Long, MatchScoringProfile> = mapOf(
				1001L to profile(1001L, idealMaritalStatus = MaritalStatus.SINGLE),
				1003L to profile(1003L, maritalStatus = MaritalStatus.DIVORCED),
			)
			val saves: MutableList<Pair<Long, Long>> = mutableListOf()

			val result = service(matchables, profiles, existsPairs = emptySet(), saves).run()

			result.recommended shouldBe 1
			saves shouldContainExactlyInAnyOrder listOf(1001L to 1003L)
		}

		it("재소개 이력이 있는 최고점 후보는 건너뛰고 다음 후보를 소개한다") {
			// 1001-1002는 이력 있음. 이상형상 1002가 최고점이지만 건너뛰고 1003과 소개.
			val matchables: List<MatchableUser> = listOf(
				male(1001L, now),
				female(1002L, now.minusMinutes(1)),
				female(1003L, now.minusMinutes(2)),
			)
			val profiles: Map<Long, MatchScoringProfile> = mapOf(
				1001L to profile(1001L, idealMaritalStatus = MaritalStatus.SINGLE),
				1002L to profile(1002L, maritalStatus = MaritalStatus.SINGLE),
				1003L to profile(1003L, maritalStatus = MaritalStatus.DIVORCED),
			)
			val saves: MutableList<Pair<Long, Long>> = mutableListOf()

			val result = service(matchables, profiles, existsPairs = setOf(1001L to 1002L), saves).run()

			result.recommended shouldBe 1
			saves shouldContainExactlyInAnyOrder listOf(1001L to 1003L)
		}
	}
})
