package com.org.meeple.api.user

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.fixture.CoinBalanceEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import io.kotest.matchers.shouldBe

/**
 * `POST /users/v1/onboarding/company-name` E2E 테스트.
 *
 * 사용자가 회사명을 직접 입력하면 프로필에 반영하고 정식 가입(ACTIVE)으로 전환하는 경로를 검증한다.
 * 공통 정리/조회 헬퍼는 [OnboardingE2ESupport] 파일의 최상위 함수로 둔다.
 */
class ResolveCompanyNameE2ETest : AbstractIntegrationSupport({

	describe("POST /users/v1/onboarding/company-name") {

		context("회사명을 직접 입력하면") {
			it("프로필에 반영되고 정식 가입(ACTIVE)된다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.COMPANY_NOT_RESOLVED),
				).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = userId, balance = 100))

				post("/users/v1/onboarding/company-name") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"companyName": "미플"}""")
				} expect {
					status(200)
					body("success", true)
				}

				userStatusOf(userId) shouldBe UserStatus.ACTIVE
				userDetailOf(userId).companyName shouldBe "미플"
				coinBalanceOf(userId) shouldBe 100
			}
		}

		context("회사명이 공백이면") {
			it("검증 실패로 400을 반환한다") {
				post("/users/v1/onboarding/company-name") {
					bearer(accessTokenFor(1L))
					jsonBody("""{"companyName": "  "}""")
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
