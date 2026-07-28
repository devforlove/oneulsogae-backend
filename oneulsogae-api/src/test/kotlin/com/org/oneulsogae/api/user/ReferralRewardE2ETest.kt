package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.infra.fixture.IdentityVerificationEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.RegionEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import io.kotest.matchers.shouldBe

/**
 * 온보딩 완료 시 추천 코드 보상 E2E 테스트.
 * 유효한 코드면 추천인·신규 유저 모두 50코인, 무효 코드면 온보딩만 성공하고 지급이 없는지 검증한다.
 * (가입 축하 100코인은 항상 지급되므로 신규 유저 잔액은 100 또는 150이 된다)
 *
 * 보상은 본인확인 DI 기준 1인 1회다 — DI가 없거나 그 DI로 이미 받은 적이 있으면 지급되지 않는다.
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
				// 추천 보상은 본인확인 DI가 있어야 지급된다.
				IntegrationUtil.persist(IdentityVerificationEntityFixture.create(userId = userId, di = "DI-REWARD-1"))
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

		context("같은 DI(동일인)가 새 계정으로 다시 추천 코드를 입력하면") {
			it("탈퇴·재가입 어뷰징으로 보고 양쪽 모두 추천 보상을 지급하지 않는다 (200)") {
				val referrer = UserEntityFixture.create(providerId = "reabuse-referrer", status = UserStatus.ACTIVE)
				referrer.referralCode = "REABUSE1"
				val referrerId: Long = IntegrationUtil.persist(referrer).id!!
				val regionId: Long = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"),
				).id!!

				// 1차 가입: 정상 지급.
				val firstUserId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "reabuse-first", status = UserStatus.ONBOARDING),
				).id!!
				IntegrationUtil.persist(IdentityVerificationEntityFixture.create(userId = firstUserId, di = "DI-SAME-PERSON"))
				post("/users/v1/onboarding/complete") {
					bearer(accessTokenFor(firstUserId))
					jsonBody(profileBodyWithReferral(regionId = regionId, referralCode = "REABUSE1"))
				} expect { status(200) }
				coinBalanceOf(referrerId) shouldBe 50

				// 2차: 탈퇴·파기 후 재가입을 흉내낸 새 계정. provider·userId는 달라도 DI는 같다.
				val secondUserId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "reabuse-second", status = UserStatus.ONBOARDING),
				).id!!
				IntegrationUtil.persist(IdentityVerificationEntityFixture.create(userId = secondUserId, di = "DI-SAME-PERSON"))

				post("/users/v1/onboarding/complete") {
					bearer(accessTokenFor(secondUserId))
					jsonBody(profileBodyWithReferral(regionId = regionId, referralCode = "REABUSE1"))
				} expect {
					status(200)
					body("success", true)
				}

				// 온보딩 자체는 성공하되 추천 보상만 빠진다. (가입 축하 100은 그대로)
				userStatusOf(secondUserId) shouldBe UserStatus.ACTIVE
				coinBalanceOf(secondUserId) shouldBe 100
				coinBalanceOf(referrerId) shouldBe 50 // 재지급 없음
				referredByOf(secondUserId) shouldBe null
			}
		}

		context("본인확인을 마치지 않아 DI가 없으면") {
			it("추천 코드가 유효해도 보상을 지급하지 않는다 (200)") {
				val referrer = UserEntityFixture.create(providerId = "nodi-referrer", status = UserStatus.ACTIVE)
				referrer.referralCode = "NODICODE"
				val referrerId: Long = IntegrationUtil.persist(referrer).id!!
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "nodi-user", status = UserStatus.ONBOARDING),
				).id!!
				val regionId: Long = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"),
				).id!!

				post("/users/v1/onboarding/complete") {
					bearer(accessTokenFor(userId))
					jsonBody(profileBodyWithReferral(regionId = regionId, referralCode = "NODICODE"))
				} expect {
					status(200)
				}

				coinBalanceOf(userId) shouldBe 100
				coinBalanceOf(referrerId) shouldBe 0
				referredByOf(userId) shouldBe null
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
		  "mbti": "ENFP",
		  "referralCode": $referralJson
		}
	""".trimIndent()
}
