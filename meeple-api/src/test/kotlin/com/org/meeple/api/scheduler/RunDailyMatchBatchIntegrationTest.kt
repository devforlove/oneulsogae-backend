package com.org.meeple.api.scheduler

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchType
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.match.entity.MatchEntity
import com.org.meeple.infra.match.entity.MatchMemberEntity
import com.org.meeple.infra.match.entity.QMatchEntity
import com.org.meeple.infra.match.entity.QMatchMemberEntity
import com.org.meeple.infra.user.entity.QUserDetailEntity
import com.org.meeple.infra.user.entity.QUserEntity
import com.org.meeple.scheduler.match.application.port.`in`.RunDailyMatchBatchUseCase
import com.org.meeple.scheduler.match.domain.MatchBatchResult
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [RunDailyMatchBatchUseCase] 통합 테스트.
 *
 * 실 컨텍스트 + Testcontainers(MySQL/Redis)에서 배치를 직접 호출한다.
 * 배치는 활성 사용자로 (반대 성별·같은 권역) Redis 풀을 스스로 적재·소비하므로, 테스트는 DB(users/user_details/matches)만 준비한다.
 * 단언은 "이 테스트가 만든 사용자 쌍"의 매칭 존재/부재로 한정해, 다른 스펙이 남긴 매칭 행에 영향받지 않게 한다.
 */
class RunDailyMatchBatchIntegrationTest(
	private val runDailyMatchBatchUseCase: RunDailyMatchBatchUseCase,
	private val redisTemplate: StringRedisTemplate,
) : AbstractIntegrationSupport({

	describe("run") {

		context("반대 성별·같은 권역의 활성 사용자가 있으면") {
			it("두 사람을 PROPOSED(DAILY)로 소개한다") {
				val maleId: Long = persistActiveUser(providerId = "p-male", gender = Gender.MALE, regionCode = 1)
				val femaleId: Long = persistActiveUser(providerId = "p-female", gender = Gender.FEMALE, regionCode = 1)

				val result: MatchBatchResult = runDailyMatchBatchUseCase.run()

				result.recommended shouldBe 1
				result.failed shouldBe 0

				// 두 사람 사이에 오늘자 PROPOSED·DAILY 소개가 정확히 하나 생긴다. (단건 조회라 중복 소개가 없음도 함께 보장)
				val match: MatchEntity = proposedMatchBetween(maleId, femaleId).shouldNotBeNull()
				match.matchType shouldBe MatchType.DAILY
				match.introducedDate shouldBe LocalDate.now()

				// 확장 씨앗: 정규화 참가자 테이블(match_members)에도 두 사람이 성별과 함께 기록된다. (male→MALE, female→FEMALE)
				matchMembersOf(match.id!!).map { it.userId to it.gender } shouldContainExactlyInAnyOrder
					listOf(maleId to Gender.MALE, femaleId to Gender.FEMALE)
			}
		}

		context("반대 성별 후보가 없으면") {
			it("아무도 소개하지 못하고 매칭을 생성하지 않는다") {
				val maleId1: Long = persistActiveUser(providerId = "p-m1", gender = Gender.MALE, regionCode = 1)
				val maleId2: Long = persistActiveUser(providerId = "p-m2", gender = Gender.MALE, regionCode = 1)

				val result: MatchBatchResult = runDailyMatchBatchUseCase.run()

				result.recommended shouldBe 0
				result.failed shouldBe 0
				matchesInvolving(maleId1).shouldBeEmpty()
				matchesInvolving(maleId2).shouldBeEmpty()
			}
		}

		context("이미 성사(MATCHED)된 사용자는") {
			it("신규 소개 대상·후보에서 제외되어 새 소개가 생기지 않는다") {
				val maleId: Long = persistActiveUser(providerId = "p-male", gender = Gender.MALE, regionCode = 1)
				val femaleId: Long = persistActiveUser(providerId = "p-female", gender = Gender.FEMALE, regionCode = 1)
				// maleId가 이미 다른 사용자와 성사(MATCHED)된 매칭을 보유한다.
				IntegrationUtil.persist(
					MatchEntityFixture.create(maleUserId = maleId, femaleUserId = 9_999L, status = MatchStatus.MATCHED),
				)

				val result: MatchBatchResult = runDailyMatchBatchUseCase.run()

				result.recommended shouldBe 0
				proposedMatchBetween(maleId, femaleId).shouldBeNull()
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QMatchMemberEntity.matchMemberEntity)
		IntegrationUtil.deleteAll(QMatchEntity.matchEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
		val poolKeys: Set<String> = redisTemplate.keys("match:pool:*")
		if (poolKeys.isNotEmpty()) redisTemplate.delete(poolKeys)
	}
})

// 정식 가입(ACTIVE) + 최근 로그인 + 성별·권역이 채워진 매칭 대상 사용자를 만들고 userId를 반환한다.
private fun persistActiveUser(providerId: String, gender: Gender, regionCode: Int): Long {
	val userId: Long = IntegrationUtil.persist(
		UserEntityFixture.create(
			providerId = providerId,
			status = UserStatus.ACTIVE,
			lastLoginAt = LocalDateTime.now().minusDays(1),
		),
	).id!!
	IntegrationUtil.persist(
		UserDetailEntityFixture.create(userId = userId, gender = gender, regionCode = regionCode),
	)
	return userId
}

// 해당 남녀 쌍의 PROPOSED 소개 한 건. (없으면 null)
private fun proposedMatchBetween(maleUserId: Long, femaleUserId: Long): MatchEntity? {
	val match: QMatchEntity = QMatchEntity.matchEntity
	return IntegrationUtil.getQuery()
		.selectFrom(match)
		.where(
			match.maleUserId.eq(maleUserId)
				.and(match.femaleUserId.eq(femaleUserId))
				.and(match.status.eq(MatchStatus.PROPOSED)),
		)
		.fetchOne()
}

// 해당 사용자가 남/녀 어느 쪽으로든 참여한 매칭 전체.
private fun matchesInvolving(userId: Long): List<MatchEntity> {
	val match: QMatchEntity = QMatchEntity.matchEntity
	return IntegrationUtil.getQuery()
		.selectFrom(match)
		.where(match.maleUserId.eq(userId).or(match.femaleUserId.eq(userId)))
		.fetch()
}

// 해당 매칭의 정규화 참가자(match_members) 행 전체.
private fun matchMembersOf(matchId: Long): List<MatchMemberEntity> {
	val member: QMatchMemberEntity = QMatchMemberEntity.matchMemberEntity
	return IntegrationUtil.getQuery()
		.selectFrom(member)
		.where(member.matchId.eq(matchId))
		.fetch()
}
