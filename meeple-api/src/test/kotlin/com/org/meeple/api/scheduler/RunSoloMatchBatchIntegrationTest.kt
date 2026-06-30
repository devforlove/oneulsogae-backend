package com.org.meeple.api.scheduler

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.match.MatchMemberStatus
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.SoloMatchType
import com.org.meeple.common.user.Gender
import com.org.meeple.core.solomatch.command.domain.MatchMembers
import com.org.meeple.infra.alarm.command.entity.AlarmEntity
import com.org.meeple.infra.alarm.command.entity.QAlarmEntity
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.fixture.SoloMatchEntityFixture
import com.org.meeple.infra.fixture.SoloMatchMemberEntityFixture
import com.org.meeple.infra.matchuser.command.entity.QMatchUserEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchMemberEntity
import com.org.meeple.infra.solomatch.command.entity.SoloMatchEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.region.entity.RegionEntity
import com.org.meeple.scheduler.solomatch.command.application.port.`in`.RunSoloMatchBatchUseCase
import com.org.meeple.scheduler.solomatch.command.domain.SoloMatchBatchResult
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [RunSoloMatchBatchUseCase](SoloMatchBatchService) 통합 테스트. 실 컨텍스트 + Testcontainers(MySQL).
 * 배치가 시작 시 regionProximityPort.refresh()로 근접 스냅샷을 적재하므로, regions·match_user를 적재한 뒤 호출한다.
 */
class RunSoloMatchBatchIntegrationTest(
	private val runSoloMatchBatchUseCase: RunSoloMatchBatchUseCase,
) : AbstractIntegrationSupport({

	describe("run") {

		context("반대 성별·가까운 지역의 활성 유저가 있으면") {
			it("두 사람을 PROPOSED(DAILY)로 소개한다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleId: Long = persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = regionId)
				val femaleId: Long = persistMatchableUser(userId = 1002L, gender = Gender.FEMALE, regionId = regionId)

				val result: SoloMatchBatchResult = runSoloMatchBatchUseCase.run()

				result.recommended shouldBe 1
				result.failed shouldBe 0
				val match: SoloMatchEntity = proposedMatchBetween(maleId, femaleId).shouldNotBeNull()
				match.matchType shouldBe SoloMatchType.DAILY
				match.introducedDate shouldBe LocalDate.now()
			}
		}

		context("반대 성별 후보가 없으면") {
			it("아무도 소개하지 못한다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleId1: Long = persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = regionId)
				persistMatchableUser(userId = 1002L, gender = Gender.MALE, regionId = regionId)

				val result: SoloMatchBatchResult = runSoloMatchBatchUseCase.run()

				result.recommended shouldBe 0
				matchesInvolving(maleId1).shouldBeEmpty()
			}
		}

		context("자격은 됐지만 끝까지 소개받지 못한 유저는") {
			it("'오늘 소개 없음' 알람을 받는다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				// 반대 성별 후보가 없어 끝까지 미매칭으로 남는다.
				val maleId: Long = persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = regionId)

				val result: SoloMatchBatchResult = runSoloMatchBatchUseCase.run()

				result.recommended shouldBe 0
				noMatchAlarms(maleId).size shouldBe 1
			}
		}

		context("오늘 소개를 받은 유저는") {
			it("'오늘 소개 없음' 알람을 받지 않는다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleId: Long = persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = regionId)
				val femaleId: Long = persistMatchableUser(userId = 1002L, gender = Gender.FEMALE, regionId = regionId)

				val result: SoloMatchBatchResult = runSoloMatchBatchUseCase.run()

				result.recommended shouldBe 1
				noMatchAlarms(maleId).shouldBeEmpty()
				noMatchAlarms(femaleId).shouldBeEmpty()
			}
		}

		context("이미 성사(MATCHED)된 유저는") {
			it("신규 소개 대상·후보에서 제외된다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleId: Long = persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = regionId)
				val femaleId: Long = persistMatchableUser(userId = 1002L, gender = Gender.FEMALE, regionId = regionId)
				persistMatch(maleId, 9001L, status = MatchStatus.MATCHED, introducedDate = LocalDate.now().minusDays(3))

				val result: SoloMatchBatchResult = runSoloMatchBatchUseCase.run()

				result.recommended shouldBe 0
				proposedMatchBetween(maleId, femaleId).shouldBeNull()
			}
		}

		context("성사(MATCHED) 매치를 나가(DEACTIVE) 매치 헤더는 MATCHED로 남은 유저는") {
			it("다시 소개 대상이 된다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val leaverId: Long = persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = regionId)
				val femaleId: Long = persistMatchableUser(userId = 1002L, gender = Gender.FEMALE, regionId = regionId)
				// 1001이 9001과의 MATCHED 매치를 나가 DEACTIVE 상태(매치 헤더는 MATCHED로 남음). 과거 소개라 '오늘 매칭' 제외엔 안 걸린다.
				persistMatchedMatchWithLeaver(leaverId = 1001L, stayerId = 9001L)

				val result: SoloMatchBatchResult = runSoloMatchBatchUseCase.run()

				result.recommended shouldBe 1
				proposedMatchBetween(leaverId, femaleId).shouldNotBeNull()
			}
		}

		context("오늘 이미 매칭된 유저는") {
			it("신규 소개 대상·후보에서 제외된다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleId: Long = persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = regionId)
				val femaleId: Long = persistMatchableUser(userId = 1002L, gender = Gender.FEMALE, regionId = regionId)
				// maleId가 오늘 다른 사람과 소개됨(introduced_date = today)
				persistMatch(maleId, 9001L, status = MatchStatus.PROPOSED, introducedDate = LocalDate.now())

				val result: SoloMatchBatchResult = runSoloMatchBatchUseCase.run()

				result.recommended shouldBe 0
				proposedMatchBetween(maleId, femaleId).shouldBeNull()
			}
		}

		context("오늘 소개됐다가 취소(소프트 삭제)된 유저는") {
			it("같은 날 다시 소개되지 않는다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleId: Long = persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = regionId)
				val femaleId: Long = persistMatchableUser(userId = 1002L, gender = Gender.FEMALE, regionId = regionId)
				// 1001이 오늘 9001과 소개됐다가 매칭을 취소해 소프트 삭제된 상태. 취소돼도 '오늘 소개 1회'는 소진된 것으로 본다.
				persistCancelledMatchToday(userIdA = 1001L, userIdB = 9001L)

				val result: SoloMatchBatchResult = runSoloMatchBatchUseCase.run()

				result.recommended shouldBe 0
				proposedMatchBetween(maleId, femaleId).shouldBeNull()
			}
		}

		context("재소개 이력이 있는(과거 소개된) 쌍은") {
			it("그 상대 대신 이력 없는 다른 후보와 소개한다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleId: Long = persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = regionId)
				val introducedFemaleId: Long = persistMatchableUser(userId = 1002L, gender = Gender.FEMALE, regionId = regionId)
				val freshFemaleId: Long = persistMatchableUser(userId = 1003L, gender = Gender.FEMALE, regionId = regionId)
				// 1001-1002는 과거 소개 이력(ux_member_key)이 있다. 성사 전(PARTIALLY)·과거 일자라 유저 제외엔 안 걸리고, 쌍 재소개만 막힌다.
				persistMatch(maleId, introducedFemaleId, status = MatchStatus.PARTIALLY_ACCEPTED, introducedDate = LocalDate.now().minusDays(3))

				val result: SoloMatchBatchResult = runSoloMatchBatchUseCase.run()

				result.recommended shouldBe 1
				proposedMatchBetween(maleId, freshFemaleId).shouldNotBeNull()    // 이력 없는 후보와 소개
				proposedMatchBetween(maleId, introducedFemaleId).shouldBeNull()  // 이력 있는 쌍은 다시 소개하지 않음
			}
		}

		context("과거 소개됐다가 취소(소프트 삭제)된 쌍은") {
			it("member_key가 남아 재소개 시 충돌하므로, 우회 조회로 이력으로 인정해 다시 소개하지 않는다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val maleId: Long = persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = regionId)
				val femaleId: Long = persistMatchableUser(userId = 1002L, gender = Gender.FEMALE, regionId = regionId)
				// 1001-1002는 과거 소개됐다가 취소돼 소프트 삭제됨(과거 일자라 '오늘 소개' 제외엔 안 걸림). ux_member_key는 deleted_at 미포함이라 그대로 남는다.
				persistCancelledPastMatch(userIdA = 1001L, userIdB = 1002L)

				val result: SoloMatchBatchResult = runSoloMatchBatchUseCase.run()

				// 우회 조회로 이력을 인정해 재소개를 막으면 유니크 충돌 없음(우회 안 하면 INSERT가 member_key 충돌로 failed).
				result.recommended shouldBe 0
				result.failed shouldBe 0
				proposedMatchBetween(maleId, femaleId).shouldBeNull()
			}
		}

		context("같은 실행 안에서") {
			it("한 사람이 두 번 소개되지 않는다") {
				val regionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = regionId)
				persistMatchableUser(userId = 1002L, gender = Gender.MALE, regionId = regionId)
				val femaleId: Long = persistMatchableUser(userId = 1003L, gender = Gender.FEMALE, regionId = regionId)

				val result: SoloMatchBatchResult = runSoloMatchBatchUseCase.run()

				result.recommended shouldBe 1
				matchesInvolving(femaleId).size shouldBe 1
			}
		}

		context("가까운 지역과 먼 지역에 후보가 있으면") {
			it("가까운 지역 후보와 소개한다") {
				val nearRegionId: Long = persistRegion("서울특별시", "강남구", 37.50, 127.00)
				val farRegionId: Long = persistRegion("부산광역시", "해운대구", 35.16, 129.16)
				// 남성이 가장 최근 로그인 → ORDER BY last_login_at DESC 기준으로 먼저 처리됨 → 근접 지역 여성과 매칭
				val now: LocalDateTime = LocalDateTime.now()
				val maleId: Long = persistMatchableUser(userId = 1001L, gender = Gender.MALE, regionId = nearRegionId, lastLoginAt = now)
				val nearFemaleId: Long = persistMatchableUser(userId = 1002L, gender = Gender.FEMALE, regionId = nearRegionId, lastLoginAt = now.minusMinutes(1))
				val farFemaleId: Long = persistMatchableUser(userId = 1003L, gender = Gender.FEMALE, regionId = farRegionId, lastLoginAt = now.minusMinutes(2))

				val result: SoloMatchBatchResult = runSoloMatchBatchUseCase.run()

				result.recommended shouldBe 1
				proposedMatchBetween(maleId, nearFemaleId).shouldNotBeNull()
				proposedMatchBetween(maleId, farFemaleId).shouldBeNull()
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
		IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
		IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QRegionEntity.regionEntity)
	}
})

private fun noMatchAlarms(userId: Long): List<AlarmEntity> {
	val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery()
		.selectFrom(alarm)
		.where(
			alarm.userId.eq(userId)
				.and(alarm.type.eq(AlarmType.ONE_TO_ONE_NO_MATCH_TODAY)),
		)
		.fetch()
}

private fun persistRegion(sido: String, sigungu: String, latitude: Double, longitude: Double): Long {
	val region: RegionEntity = IntegrationUtil.persist(
		RegionEntityFixture.create(sido = sido, sigungu = sigungu, latitude = latitude, longitude = longitude),
	)
	return region.id!!
}

private fun persistMatchableUser(userId: Long, gender: Gender, regionId: Long, lastLoginAt: LocalDateTime = LocalDateTime.now()): Long {
	IntegrationUtil.persist(
		MatchUserEntityFixture.create(userId = userId, gender = gender, regionId = regionId, lastLoginAt = lastLoginAt),
	)
	return userId
}

private fun persistMatch(userIdA: Long, userIdB: Long, status: MatchStatus, introducedDate: LocalDate) {
	val match: SoloMatchEntity = IntegrationUtil.persist(
		SoloMatchEntityFixture.create(
			memberKey = MatchMembers.memberKeyOf(listOf(userIdA, userIdB)),
			status = status,
			introducedDate = introducedDate,
		),
	)
	// 성사(MATCHED) 매치의 멤버는 ACTIVE, 그 외(PROPOSED 등)는 WAITING.
	val memberStatus: MatchMemberStatus = if (status == MatchStatus.MATCHED) MatchMemberStatus.ACTIVE else MatchMemberStatus.WAITING
	IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = userIdA, gender = Gender.MALE, status = memberStatus))
	IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = userIdB, gender = Gender.FEMALE, status = memberStatus))
}

// [userIdA]가 오늘 [userIdB]와 소개됐다가 매칭을 취소해 헤더·참가자가 모두 소프트 삭제된 상태를 만든다.
// (softDelete()로 deleted_at을 채워 INSERT — @SQLRestriction은 INSERT엔 영향 없음)
private fun persistCancelledMatchToday(userIdA: Long, userIdB: Long) {
	val match: SoloMatchEntity = IntegrationUtil.persist(
		SoloMatchEntityFixture.create(
			memberKey = MatchMembers.memberKeyOf(listOf(userIdA, userIdB)),
			status = MatchStatus.CLOSED,
			introducedDate = LocalDate.now(),
		).apply { softDelete() },
	)
	IntegrationUtil.persist(
		SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = userIdA, gender = Gender.MALE, status = MatchMemberStatus.DEACTIVE).apply { softDelete() },
	)
	IntegrationUtil.persist(
		SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = userIdB, gender = Gender.FEMALE, status = MatchMemberStatus.DEACTIVE).apply { softDelete() },
	)
}

// [leaverId]가 [stayerId]와의 MATCHED 매치를 나가 DEACTIVE인 상태를 만든다. (매치 헤더는 MATCHED, leaver만 DEACTIVE)
private fun persistMatchedMatchWithLeaver(leaverId: Long, stayerId: Long) {
	val match: SoloMatchEntity = IntegrationUtil.persist(
		SoloMatchEntityFixture.create(
			memberKey = MatchMembers.memberKeyOf(listOf(leaverId, stayerId)),
			status = MatchStatus.MATCHED,
			introducedDate = LocalDate.now().minusDays(5),
		),
	)
	IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = leaverId, gender = Gender.MALE, status = MatchMemberStatus.DEACTIVE))
	IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = stayerId, gender = Gender.FEMALE, status = MatchMemberStatus.ACTIVE))
}

// [userIdA]가 과거 [userIdB]와 소개됐다가 취소해 헤더·참가자가 소프트 삭제된 상태를 만든다(과거 일자).
// ux_member_key는 deleted_at을 포함하지 않아 member_key가 그대로 남으므로, 재소개 시 충돌한다 → existsByPair가 이 삭제 행을 봐야 한다.
private fun persistCancelledPastMatch(userIdA: Long, userIdB: Long) {
	val match: SoloMatchEntity = IntegrationUtil.persist(
		SoloMatchEntityFixture.create(
			memberKey = MatchMembers.memberKeyOf(listOf(userIdA, userIdB)),
			status = MatchStatus.CLOSED,
			introducedDate = LocalDate.now().minusDays(3),
		).apply { softDelete() },
	)
	IntegrationUtil.persist(
		SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = userIdA, gender = Gender.MALE, status = MatchMemberStatus.DEACTIVE).apply { softDelete() },
	)
	IntegrationUtil.persist(
		SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = userIdB, gender = Gender.FEMALE, status = MatchMemberStatus.DEACTIVE).apply { softDelete() },
	)
}

private fun proposedMatchBetween(userIdA: Long, userIdB: Long): SoloMatchEntity? {
	val match: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
	return IntegrationUtil.getQuery()
		.selectFrom(match)
		.where(
			match.memberKey.eq(MatchMembers.memberKeyOf(listOf(userIdA, userIdB)))
				.and(match.status.eq(MatchStatus.PROPOSED)),
		)
		.fetchOne()
}

private fun matchesInvolving(userId: Long): List<SoloMatchEntity> {
	val match: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
	val member: QSoloMatchMemberEntity = QSoloMatchMemberEntity.soloMatchMemberEntity
	return IntegrationUtil.getQuery()
		.select(match)
		.from(member)
		.join(match).on(match.id.eq(member.matchId))
		.where(member.userId.eq(userId))
		.fetch()
}
