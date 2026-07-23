package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.RegionEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import io.kotest.matchers.shouldBe

/**
 * 온보딩 완료 시 추천 코드 보상 E2E 테스트.
 * 유효한 코드면 추천인·신규 유저 모두 50코인, 무효 코드면 온보딩만 성공하고 지급이 없는지 검증한다.
 * (가입 축하 100코인은 항상 지급되므로 신규 유저 잔액은 100 또는 150이 된다)
 */
class ReferralRewardE2ETest : AbstractIntegrationSupport({

	describe("POST /users/v1/onboarding/complete + referralCode") {

		context("유효한 추천 코드로 온보딩을 완료하면") {
			it("추천인·신규 유저 모두 50코인을 받고 추천인이 기록된다 (200)") {
				val referrer = UserEntityFixture.create(providerId = "referrer-provider-id", status = UserStatus.ACTIVE)
				referrer.referralCode = "REFER123"
				val referrerId: Long = IntegrationUtil.persist(referrer).id!!

				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ONBOARDING),
				).id!!
				val regionId: Long = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"),
				).id!!

				post("/users/v1/onboarding/complete") {
					bearer(accessTokenFor(userId))
					jsonBody(profileBodyWithReferral(regionId = regionId, referralCode = "REFER123"))
				} expect {
					status(200)
					body("success", true)
				}

				userStatusOf(userId) shouldBe UserStatus.ACTIVE
				coinBalanceOf(userId) shouldBe 150 // 가입 축하 100 + 추천 보상 50
				coinBalanceOf(referrerId) shouldBe 50
				referredByOf(userId) shouldBe referrerId
			}
		}

		context("존재하지 않는 추천 코드로 온보딩을 완료하면") {
			it("온보딩은 성공하고 추천 보상만 지급되지 않는다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ONBOARDING),
				).id!!
				val regionId: Long = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"),
				).id!!

				post("/users/v1/onboarding/complete") {
					bearer(accessTokenFor(userId))
					jsonBody(profileBodyWithReferral(regionId = regionId, referralCode = "NOSUCH00"))
				} expect {
					status(200)
					body("success", true)
				}

				userStatusOf(userId) shouldBe UserStatus.ACTIVE
				coinBalanceOf(userId) shouldBe 100 // 가입 축하만
				referredByOf(userId) shouldBe null
			}
		}

		context("추천 코드 없이 온보딩을 완료하면") {
			it("기존과 동일하게 가입 축하 코인만 지급된다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ONBOARDING),
				).id!!
				val regionId: Long = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"),
				).id!!

				post("/users/v1/onboarding/complete") {
					bearer(accessTokenFor(userId))
					jsonBody(profileBodyWithReferral(regionId = regionId, referralCode = null))
				} expect {
					status(200)
					body("success", true)
				}

				coinBalanceOf(userId) shouldBe 100
				referredByOf(userId) shouldBe null
			}
		}
	}

	afterTest {
		cleanupOnboarding()
	}
})

/** referralCode를 선택 포함하는 온보딩 완료 바디. (다른 필드는 CompleteOnboardingE2ETest의 fullProfileBody와 동일 값) */
private fun profileBodyWithReferral(regionId: Long, referralCode: String?): String {
	val referralJson: String = referralCode?.let { "\"$it\"" } ?: "null"
	return """
		{
		  "nickname": "테스트유저",
		  "birthday": "1995-01-01",
		  "height": 175,
		  "gender": "MALE",
		  "phoneNumber": "010-1234-5678",
		  "job": "개발자",
		  "regionId": $regionId,
		  "introduction": "안녕하세요 잘 부탁드립니다.",
		  "traits": ["성실함"],
		  "interests": ["영화"],
		  "maritalStatus": "SINGLE",
		  "smokingStatus": "NON_SMOKER",
		  "religion": "NONE",
		  "drinkingStatus": "SOMETIMES",
		  "bodyType": "MALE_NORMAL",
		  "referralCode": $referralJson
		}
	""".trimIndent()
}
