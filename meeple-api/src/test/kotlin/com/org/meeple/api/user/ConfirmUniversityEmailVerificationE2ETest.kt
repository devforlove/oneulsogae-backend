package com.org.meeple.api.user

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.UniversityEmailVerificationEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.fixture.UserUniversityEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.user.command.entity.QUniversityEmailVerificationEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.QUserUniversityEntity
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * `POST /users/v1/university-email/verifications/confirm` E2E 테스트.
 *
 * 저장된 인증번호와 입력값을 비교해, 학교 도메인 매핑 결과에 따라 학교 이메일/학교명을 프로필(user_details)·매칭 읽기 모델(match_user)에 기록하는 경로를 검증한다.
 * (온보딩이 아니므로 가입 상태·코인은 변하지 않는다)
 */
class ConfirmUniversityEmailVerificationE2ETest : AbstractIntegrationSupport({

	describe("POST /users/v1/university-email/verifications/confirm") {

		context("저장된 인증번호와 일치하고 학교 도메인이 매핑되면") {
			it("학교명이 확정돼 user_details·match_user에 기록된다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))
				// 매칭 풀에 적재된 사용자라면 match_user에도 학교명이 기록돼야 한다.
				IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = Gender.MALE, regionId = 1L))
				IntegrationUtil.persist(
					UniversityEmailVerificationEntityFixture.create(
						userId = userId,
						universityEmail = "student@snu.ac.kr",
						code = "123456",
					),
				)
				IntegrationUtil.persist(UserUniversityEntityFixture.create(emailDomain = "snu.ac.kr", universityName = "서울대학교"))

				post("/users/v1/university-email/verifications/confirm") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"code": "123456"}""")
				} expect {
					status(200)
					body("success", true)
					body("data.universityName", "서울대학교")
				}

				val detail: com.org.meeple.infra.user.command.entity.UserDetailEntity = userDetailOf(userId)
				detail.universityEmail shouldBe "student@snu.ac.kr"
				detail.universityName shouldBe "서울대학교"
				// 가입 상태는 그대로(온보딩 아님).
				userStatusOf(userId) shouldBe UserStatus.ACTIVE
				// match_user에도 학교명이 기록된다.
				matchUserUniversityNameOf(userId) shouldBe "서울대학교"
			}
		}

		context("인증번호는 맞지만 학교 도메인 매핑이 없으면") {
			it("USER-016으로 실패하고 학교 정보가 기록되지 않는다 (400)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))
				IntegrationUtil.persist(
					UniversityEmailVerificationEntityFixture.create(
						userId = userId,
						universityEmail = "student@unknown-univ.ac.kr",
						code = "123456",
					),
				)

				post("/users/v1/university-email/verifications/confirm") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"code": "123456"}""")
				} expect {
					status(400)
					body("success", false)
					body("error.code", "USER-016")
				}

				userDetailOf(userId).universityEmail.shouldBeNull()
			}
		}

		context("인증번호가 일치하지 않으면") {
			it("USER-006으로 실패하고 학교 정보가 기록되지 않는다 (400)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))
				IntegrationUtil.persist(
					UniversityEmailVerificationEntityFixture.create(userId = userId, code = "123456"),
				)

				post("/users/v1/university-email/verifications/confirm") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"code": "999999"}""")
				} expect {
					status(400)
					body("success", false)
					body("error.code", "USER-006")
				}

				userDetailOf(userId).universityEmail.shouldBeNull()
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QUniversityEmailVerificationEntity.universityEmailVerificationEntity)
		IntegrationUtil.deleteAll(QUserUniversityEntity.userUniversityEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})

private fun matchUserUniversityNameOf(userId: Long): String? =
	IntegrationUtil.getQuery()
		.select(QMatchUserEntity.matchUserEntity.universityName)
		.from(QMatchUserEntity.matchUserEntity)
		.where(QMatchUserEntity.matchUserEntity.userId.eq(userId))
		.fetchOne()
