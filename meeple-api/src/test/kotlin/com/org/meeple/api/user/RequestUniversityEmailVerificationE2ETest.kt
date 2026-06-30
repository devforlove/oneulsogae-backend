package com.org.meeple.api.user

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.fixture.UserUniversityEntityFixture
import com.org.meeple.infra.user.command.entity.QUniversityEmailVerificationEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.QUserUniversityEntity
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.notNullValue

/**
 * `POST /users/v1/university-email/verifications` E2E 테스트.
 *
 * 입력한 학교 이메일로 인증번호를 발급하는 경로를 검증한다. (온보딩과 무관한 선택적 추가 인증 — 가입 상태를 바꾸지 않는다)
 */
class RequestUniversityEmailVerificationE2ETest : AbstractIntegrationSupport({

	describe("POST /users/v1/university-email/verifications") {

		context("등록된 학교 이메일로 가입된 사용자가 인증을 요청하면") {
			it("인증번호가 발급되고 인증 요청 1건이 생성된다 (200, 가입 상태는 그대로)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!
				// 등록된 학교 도메인이라야 발송된다.
				IntegrationUtil.persist(UserUniversityEntityFixture.create(emailDomain = "snu.ac.kr", universityName = "서울대학교"))

				post("/users/v1/university-email/verifications") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"universityEmail": "student@snu.ac.kr"}""")
				} expect {
					status(200)
					body("success", true)
					body("data.universityEmail", "student@snu.ac.kr")
					body("data.expiresAt", notNullValue())
				}

				universityVerificationCountOf(userId) shouldBe 1
				// 학교 인증은 온보딩이 아니므로 가입 상태를 바꾸지 않는다.
				userStatusOf(userId) shouldBe UserStatus.ACTIVE
			}
		}

		context("개인 이메일로 학교 인증을 요청하면") {
			it("직장/학교 인증에 쓸 수 없어 USER-008로 실패한다 (400)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!

				post("/users/v1/university-email/verifications") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"universityEmail": "student@gmail.com"}""")
				} expect {
					status(400)
					body("success", false)
					body("error.code", "USER-008")
				}

				universityVerificationCountOf(userId) shouldBe 0
			}
		}

		context("다른 사용자가 이미 인증해 쓰고 있는 학교 이메일로 인증을 요청하면") {
			it("발송 없이 409(UNIVERSITY_EMAIL_ALREADY_USED)를 반환한다") {
				IntegrationUtil.persist(UserUniversityEntityFixture.create(emailDomain = "snu.ac.kr", universityName = "서울대학교"))
				// 다른 사용자가 이미 해당 학교 이메일을 인증해 프로필에 보유한 상태.
				val otherUserId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(
						providerId = "other-provider-id",
						email = "other@test.com",
						status = UserStatus.ACTIVE,
					),
				).id!!
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = otherUserId, universityEmail = "student@snu.ac.kr"),
				)

				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!

				post("/users/v1/university-email/verifications") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"universityEmail": "student@snu.ac.kr"}""")
				} expect {
					status(409)
					body("success", false)
					body("error.code", "USER-018")
				}

				universityVerificationCountOf(userId) shouldBe 0
			}
		}

		context("등록되지 않은 학교 이메일로 인증을 요청하면") {
			it("발송 없이 USER-016으로 실패한다 (400)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!
				// user_universities에 매핑이 없는 도메인.

				post("/users/v1/university-email/verifications") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"universityEmail": "student@unknown-univ.ac.kr"}""")
				} expect {
					status(400)
					body("success", false)
					body("error.code", "USER-016")
				}

				universityVerificationCountOf(userId) shouldBe 0
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QUniversityEmailVerificationEntity.universityEmailVerificationEntity)
		IntegrationUtil.deleteAll(QUserUniversityEntity.userUniversityEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})

private fun universityVerificationCountOf(userId: Long): Int =
	IntegrationUtil.getQuery()
		.selectFrom(QUniversityEmailVerificationEntity.universityEmailVerificationEntity)
		.where(QUniversityEmailVerificationEntity.universityEmailVerificationEntity.userId.eq(userId))
		.fetch()
		.size
