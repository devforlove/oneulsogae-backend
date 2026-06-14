package com.org.meeple.api.user

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.fixture.CompanyEmailVerificationEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserCompanyEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.nullValue

/**
 * `POST /users/v1/onboarding/company-email/verifications/confirm` E2E 테스트.
 *
 * 저장된 인증번호와 입력값을 비교해, 회사 도메인 매핑 결과에 따라 정식 가입(ACTIVE)/회사명 미확정(COMPANY_NOT_RESOLVED)으로 확정하는 경로를 검증한다.
 * 공통 정리/조회 헬퍼는 [OnboardingE2ESupport] 파일의 최상위 함수로 둔다.
 */
class ConfirmCompanyEmailVerificationE2ETest : AbstractIntegrationSupport({

	describe("POST /users/v1/onboarding/company-email/verifications/confirm") {

		context("저장된 인증번호와 일치하고 회사 도메인이 매핑되면") {
			it("정식 가입(ACTIVE)되고 회사명이 확정된다 (200, isCompanyResolved=true)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ONBOARDING),
				).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))
				IntegrationUtil.persist(
					CompanyEmailVerificationEntityFixture.create(
						userId = userId,
						companyEmail = "user@meeple.com",
						code = "123456",
					),
				)
				IntegrationUtil.persist(UserCompanyEntityFixture.create(emailDomain = "meeple.com", companyName = "미플"))

				post("/users/v1/onboarding/company-email/verifications/confirm") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"code": "123456"}""")
				} expect {
					status(200)
					body("success", true)
					body("data.isCompanyResolved", true)
					body("data.companyName", "미플")
				}

				userStatusOf(userId) shouldBe UserStatus.ACTIVE
				userDetailOf(userId).companyName shouldBe "미플"
			}
		}

		context("인증번호는 맞지만 회사 도메인 매핑이 없으면") {
			it("회사명 미확정(COMPANY_NOT_RESOLVED) 상태가 된다 (200, isCompanyResolved=false)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ONBOARDING),
				).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))
				IntegrationUtil.persist(
					CompanyEmailVerificationEntityFixture.create(
						userId = userId,
						companyEmail = "user@unknown-corp.com",
						code = "123456",
					),
				)

				post("/users/v1/onboarding/company-email/verifications/confirm") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"code": "123456"}""")
				} expect {
					status(200)
					body("success", true)
					body("data.isCompanyResolved", false)
					body("data.companyName", nullValue())
				}

				userStatusOf(userId) shouldBe UserStatus.COMPANY_NOT_RESOLVED
			}
		}

		context("인증번호가 일치하지 않으면") {
			it("USER-006으로 실패하고 가입 상태가 그대로다 (400)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ONBOARDING),
				).id!!
				IntegrationUtil.persist(
					CompanyEmailVerificationEntityFixture.create(userId = userId, code = "123456"),
				)

				post("/users/v1/onboarding/company-email/verifications/confirm") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"code": "999999"}""")
				} expect {
					status(400)
					body("success", false)
					body("error.code", "USER-006")
				}

				userStatusOf(userId) shouldBe UserStatus.ONBOARDING
			}
		}
	}

	afterTest {
		cleanupOnboarding()
	}
})
