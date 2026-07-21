package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.infra.fixture.CompanyEmailVerificationEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserCompanyEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import io.kotest.matchers.shouldBe

/**
 * `POST /users/v1/onboarding/company-email/verifications/confirm` E2E 테스트.
 *
 * 마이탭 회사 인증(온보딩과 분리된 부가 인증) 경로를 검증한다: 저장된 인증번호와 입력값을 비교해
 * 회사 이메일·회사명을 프로필에 확정 반영한다. **가입 상태는 바꾸지 않고, 가입 축하 코인도 지급하지 않는다.**
 * (온보딩 완료·코인·첫 소개는 POST /users/v1/onboarding/complete 경로가 담당한다)
 * 공통 정리/조회 헬퍼는 [OnboardingE2ESupport] 파일의 최상위 함수로 둔다.
 */
class ConfirmCompanyEmailVerificationE2ETest : AbstractIntegrationSupport({

	describe("POST /users/v1/onboarding/company-email/verifications/confirm") {

		context("저장된 인증번호와 일치하고 회사 도메인이 매핑되면") {
			it("회사명이 프로필에 확정되고, 가입 상태·코인은 변하지 않는다 (200, isCompanyResolved=true)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))
				IntegrationUtil.persist(
					CompanyEmailVerificationEntityFixture.create(
						userId = userId,
						companyEmail = "user@oneulsogae.com",
						code = "123456",
					),
				)
				IntegrationUtil.persist(UserCompanyEntityFixture.create(emailDomain = "oneulsogae.com", companyName = "오늘의 소개"))

				post("/users/v1/onboarding/company-email/verifications/confirm") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"code": "123456"}""")
				} expect {
					status(200)
					body("success", true)
					body("data.isCompanyResolved", true)
					body("data.companyName", "오늘의 소개")
				}

				// 프로필에는 회사 이메일·회사명이 확정 반영된다.
				userDetailOf(userId).companyEmail shouldBe "user@oneulsogae.com"
				userDetailOf(userId).companyName shouldBe "오늘의 소개"
				// 마이탭 부가 인증이므로 가입 상태는 그대로이고, 가입 축하 코인도 지급되지 않는다.
				userStatusOf(userId) shouldBe UserStatus.ACTIVE
				coinBalanceOf(userId) shouldBe 0
			}
		}

		context("인증번호는 맞지만 회사 도메인 매핑이 없으면") {
			it("USER-034로 인증이 실패하고 프로필에 아무것도 반영되지 않는다 (400)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
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
					status(400)
					body("success", false)
					body("error.code", "USER-034")
				}

				// 회사명 매핑을 찾지 못하면 인증 전체가 롤백되어 회사 이메일·회사명이 확정되지 않고, 가입 상태도 그대로다.
				userDetailOf(userId).companyEmail shouldBe null
				userDetailOf(userId).companyName shouldBe null
				userStatusOf(userId) shouldBe UserStatus.ACTIVE
			}
		}

		context("인증번호가 일치하지 않으면") {
			it("USER-006으로 실패하고 프로필에 아무것도 반영되지 않는다 (400)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))
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

				userDetailOf(userId).companyEmail shouldBe null
			}
		}
	}

	afterTest {
		cleanupOnboarding()
	}
})
