package com.org.meeple.api.scheduler

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserIdealTypeEntity
import com.org.meeple.infra.user.command.entity.UserIdealTypeEntity
import com.org.meeple.scheduler.solomatch.query.dao.GetMatchScoringProfileDao
import com.org.meeple.scheduler.solomatch.query.dto.MatchScoringProfile
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate

/**
 * [GetMatchScoringProfileDao] 통합 테스트. user_details + user_ideal_types 조인 투영과 나이 계산을 검증한다.
 */
class GetMatchScoringProfileDaoIntegrationTest(
	private val getMatchScoringProfileDao: GetMatchScoringProfileDao,
) : AbstractIntegrationSupport({

	describe("load") {

		it("user_details의 속성과 나이(today 기준), 이상형을 함께 투영한다") {
			IntegrationUtil.persist(
				UserDetailEntityFixture.create(userId = 1L, gender = Gender.FEMALE, birthday = LocalDate.of(1996, 1, 1)),
			)
			IntegrationUtil.persist(
				UserIdealTypeEntity(userId = 1L, ageMin = 28, ageMax = 34, maritalStatus = MaritalStatus.SINGLE),
			)

			val profiles: Map<Long, MatchScoringProfile> = getMatchScoringProfileDao.load(setOf(1L), LocalDate.of(2026, 7, 1))

			val profile: MatchScoringProfile = profiles[1L].shouldNotBeNull()
			profile.age shouldBe 30
			profile.idealAgeMin shouldBe 28
			profile.idealAgeMax shouldBe 34
			profile.idealMaritalStatus shouldBe MaritalStatus.SINGLE
		}

		it("이상형이 없으면 이상형 필드는 null(속성만 채워진다)") {
			IntegrationUtil.persist(
				UserDetailEntityFixture.create(userId = 2L, gender = Gender.MALE, birthday = LocalDate.of(1990, 6, 1)),
			)

			val profiles: Map<Long, MatchScoringProfile> = getMatchScoringProfileDao.load(setOf(2L), LocalDate.of(2026, 7, 1))

			val profile: MatchScoringProfile = profiles[2L].shouldNotBeNull()
			profile.age shouldBe 36
			profile.idealAgeMin.shouldBeNull()
			profile.idealMaritalStatus.shouldBeNull()
		}

		it("빈 userIds면 빈 맵을 돌려준다") {
			getMatchScoringProfileDao.load(emptySet(), LocalDate.of(2026, 7, 1)) shouldBe emptyMap()
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QUserIdealTypeEntity.userIdealTypeEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}
})
