package com.org.meeple.api.scheduler

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.SoloMatchType
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.UserStatus
import com.org.meeple.core.match.command.domain.MatchMembers
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.SoloMatchEntityFixture
import com.org.meeple.infra.fixture.SoloMatchMemberEntityFixture
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.match.command.entity.SoloMatchEntity
import com.org.meeple.infra.match.command.entity.SoloMatchMemberEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchMemberEntity
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.scheduler.match.command.application.port.`in`.RunDailyMatchBatchUseCase
import com.org.meeple.scheduler.match.command.domain.MatchBatchResult
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
				val match: SoloMatchEntity = proposedMatchBetween(maleId, femaleId).shouldNotBeNull()
				match.matchType shouldBe SoloMatchType.DAILY
				match.introducedDate shouldBe LocalDate.now()

				// 확장 씨앗: 정규화 참가자 테이블(solo_match_members)에도 두 사람이 성별과 함께 기록된다. (male→MALE, female→FEMALE)
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
				// maleId가 이미 다른 사용자와 성사(MATCHED)된 매칭을 보유한다. (참가자 행으로 기록)
				persistMatchedMatch(maleId, 9_999L)

				val result: MatchBatchResult = runDailyMatchBatchUseCase.run()

				result.recommended shouldBe 0
				proposedMatchBetween(maleId, femaleId).shouldBeNull()
			}
		}

		context("같은 배치 실행 안에서") {
			it("상대로 소개된 사용자가 뒤이어 대상이 돼도 두 번 소개되지 않는다") {
				// 남성 2명·여성 1명. 한 남성이 여성과 소개되면, 여성은 본인 차례에 '오늘 소개됨'으로 건너뛰어 다른 남성과 또 소개되지 않는다.
				persistActiveUser(providerId = "p-m1", gender = Gender.MALE, regionCode = 1)
				persistActiveUser(providerId = "p-m2", gender = Gender.MALE, regionCode = 1)
				val femaleId: Long = persistActiveUser(providerId = "p-female", gender = Gender.FEMALE, regionCode = 1)

				val result: MatchBatchResult = runDailyMatchBatchUseCase.run()

				// 소개는 정확히 1건, 여성은 정확히 한 매칭에만 속한다. (이중 소개 방지)
				result.recommended shouldBe 1
				matchesInvolving(femaleId).size shouldBe 1
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QSoloMatchMemberEntity.soloMatchMemberEntity)
		IntegrationUtil.deleteAll(QSoloMatchEntity.soloMatchEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
		val poolKeys: Set<String> = redisTemplate.keys("match:pool:*")
		if (poolKeys.isNotEmpty()) redisTemplate.delete(poolKeys)
	}
})

// 정식 가입(ACTIVE) + 최근 로그인 + 성별·권역이 채워진 매칭 대상 사용자를 만들고 userId를 반환한다.
// 배치 대상 조회는 매칭 읽기 모델(match_user)을 보므로, 정식 가입 사용자에 해당하는 match_user 행도 함께 적재한다.
private fun persistActiveUser(providerId: String, gender: Gender, regionCode: Int): Long {
	val lastLoginAt: LocalDateTime = LocalDateTime.now().minusDays(1)
	val userId: Long = IntegrationUtil.persist(
		UserEntityFixture.create(
			providerId = providerId,
			status = UserStatus.ACTIVE,
			lastLoginAt = lastLoginAt,
		),
	).id!!
	IntegrationUtil.persist(
		UserDetailEntityFixture.create(userId = userId, gender = gender, regionCode = regionCode),
	)
	IntegrationUtil.persist(
		MatchUserEntityFixture.create(userId = userId, gender = gender, regionCode = regionCode, lastLoginAt = lastLoginAt),
	)
	return userId
}

// 1:1 성사(MATCHED) 매칭 헤더 + 두 참가자 행을 저장한다. (이미 매칭된 사용자 제외는 참가자 조인으로 판단되므로 참가자 행이 있어야 한다)
private fun persistMatchedMatch(maleUserId: Long, femaleUserId: Long) {
	val match: SoloMatchEntity = IntegrationUtil.persist(
		SoloMatchEntityFixture.create(
			memberKey = MatchMembers.memberKeyOf(listOf(maleUserId, femaleUserId)),
			status = MatchStatus.MATCHED,
		),
	)
	IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = maleUserId, gender = Gender.MALE, accepted = true))
	IntegrationUtil.persist(SoloMatchMemberEntityFixture.create(matchId = match.id!!, userId = femaleUserId, gender = Gender.FEMALE, accepted = true))
}

// 해당 쌍의 PROPOSED 소개 한 건. (없으면 null) 참가자 조합 키로 찾는다.
private fun proposedMatchBetween(maleUserId: Long, femaleUserId: Long): SoloMatchEntity? {
	val match: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
	return IntegrationUtil.getQuery()
		.selectFrom(match)
		.where(
			match.memberKey.eq(MatchMembers.memberKeyOf(listOf(maleUserId, femaleUserId)))
				.and(match.status.eq(MatchStatus.PROPOSED)),
		)
		.fetchOne()
}

// 해당 사용자가 참가자로 들어간 매칭 전체. (solo_match_members ⋈ matches)
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

// 해당 매칭의 정규화 참가자(solo_match_members) 행 전체.
private fun matchMembersOf(matchId: Long): List<SoloMatchMemberEntity> {
	val member: QSoloMatchMemberEntity = QSoloMatchMemberEntity.soloMatchMemberEntity
	return IntegrationUtil.getQuery()
		.selectFrom(member)
		.where(member.matchId.eq(matchId))
		.fetch()
}
