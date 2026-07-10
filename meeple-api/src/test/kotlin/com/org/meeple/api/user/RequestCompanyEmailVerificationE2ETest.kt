package com.org.meeple.api.user

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.notNullValue

/**
 * `POST /users/v1/onboarding/company-email/verifications` E2E 테스트.
 *
 * 입력한 회사 이메일로 인증번호를 발급하는 경로(온보딩과 분리된 회사 인증)를 검증한다.
 * 이 엔드포인트는 프로필 저장·가입 상태 전환을 하지 않고 인증번호 발급만 담당한다.
 * 공통 정리/조회 헬퍼는 [OnboardingE2ESupport] 파일의 최상위 함수로 둔다.
 */
class RequestCompanyEmailVerificationE2ETest : AbstractIntegrationSupport({

	describe("POST /users/v1/onboarding/company-email/verifications") {

		context("회사 이메일로 인증을 요청하면") {
			it("인증번호가 발급된다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!

				post("/users/v1/onboarding/company-email/verifications") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"companyEmail": "user@meeple.com"}""")
				} expect {
					status(200)
					body("success", true)
					body("data.companyEmail", "user@meeple.com")
					body("data.expiresAt", notNullValue())
				}

				verificationCountOf(userId) shouldBe 1
			}
		}

		context("다른 사용자가 이미 인증해 쓰고 있는 회사 이메일로 인증을 요청하면") {
			it("부수효과 없이 409(COMPANY_EMAIL_ALREADY_USED)를 반환한다") {
				// 다른 사용자가 이미 해당 회사 이메일을 인증해 프로필에 보유한 상태.
				val otherUserId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(
						providerId = "other-provider-id",
						email = "other@test.com",
						status = UserStatus.ACTIVE,
					),
				).id!!
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = otherUserId, companyEmail = "user@meeple.com"),
				)

				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!

				post("/users/v1/onboarding/company-email/verifications") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"companyEmail": "user@meeple.com"}""")
				} expect {
					status(409)
					body("success", false)
					body("error.code", "USER-017")
				}

				// 중복으로 막혔으므로 인증번호 발급 등 부수효과가 없어야 한다.
				verificationCountOf(userId) shouldBe 0
			}
		}

		context("개인 이메일 도메인으로 인증을 요청하면") {
			it("직장 인증 불가로 400(PERSONAL_EMAIL_NOT_ALLOWED)를 반환한다") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!

				post("/users/v1/onboarding/company-email/verifications") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"companyEmail": "user@gmail.com"}""")
				} expect {
					status(400)
					body("success", false)
					body("error.code", "USER-008")
				}

				verificationCountOf(userId) shouldBe 0
			}
		}

		context("이메일 형식이 올바르지 않으면") {
			it("도메인에 닿기 전 검증 실패로 400을 반환한다") {
				post("/users/v1/onboarding/company-email/verifications") {
					bearer(accessTokenFor(1L))
					jsonBody("""{"companyEmail": "not-an-email"}""")
				} expect {
					status(400)
					body("success", false)
					body("error.code", "INVALID_REQUEST")
				}
			}
		}
	}

	afterTest {
		cleanupOnboarding()
	}
})
