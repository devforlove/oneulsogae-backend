package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.coin.CoinPolicy
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.hamcrest.Matchers.notNullValue

/**
 * `GET /users/v1/me/referral-code` E2E 테스트.
 * 추천 코드 lazy 발급(get-or-create)과 재호출 멱등성을 검증한다.
 */
class ReferralCodeE2ETest : AbstractIntegrationSupport({

	describe("GET /users/v1/me/referral-code") {

		context("아직 추천 코드가 없는 유저가 조회하면") {
			it("A-Z0-9 8자 코드가 발급되어 저장·반환된다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!

				val response = get("/users/v1/me/referral-code") {
					bearer(accessTokenFor(userId))
				}
				response expect {
					status(200)
					body("success", true)
					body("data.referralCode", notNullValue())
				}
				val code: String = response.extract().path("data.referralCode")

				code shouldMatch Regex("^[A-Z0-9]{8}$")
				referralCodeOf(userId) shouldBe code
			}
		}

		context("이미 코드가 있는 유저가 다시 조회하면") {
			it("같은 코드를 그대로 반환한다") {
				val user = UserEntityFixture.create(status = UserStatus.ACTIVE)
				user.referralCode = "FIXED123"
				val userId: Long = IntegrationUtil.persist(user).id!!

				get("/users/v1/me/referral-code") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.referralCode", "FIXED123")
				}
			}
		}

		context("추천한 친구가 아직 없으면") {
			it("추천 실적은 0명·0코인이다") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "referral-summary-none", status = UserStatus.ACTIVE),
				).id!!

				get("/users/v1/me/referral-code") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.referredUserCount", 0)
					body("data.earnedCoinAmount", 0)
				}
			}
		}

		context("내 코드로 가입한 친구가 2명이면") {
			it("추천 실적은 2명과 보상 단가 × 2코인이다 (내가 추천받아 받은 보상은 빼고)") {
				val referrer = UserEntityFixture.create(providerId = "referral-summary-referrer", status = UserStatus.ACTIVE)
				// 이 유저 자신도 남의 코드로 가입해 REFERRAL 보상을 받았지만, 그 코인은 내 실적에 잡히지 않아야 한다.
				val inviterId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "referral-summary-inviter", status = UserStatus.ACTIVE),
				).id!!
				referrer.referredByUserId = inviterId
				val referrerId: Long = IntegrationUtil.persist(referrer).id!!

				(1..2).forEach { index: Int ->
					val referred = UserEntityFixture.create(providerId = "referral-summary-friend-$index", status = UserStatus.ACTIVE)
					referred.referredByUserId = referrerId
					IntegrationUtil.persist(referred)
				}

				get("/users/v1/me/referral-code") {
					bearer(accessTokenFor(referrerId))
				} expect {
					status(200)
					body("data.referredUserCount", 2)
					body("data.earnedCoinAmount", CoinPolicy.REFERRAL_REWARD_COIN_AMOUNT * 2)
				}
			}
		}
	}

	afterTest {
		cleanupOnboarding()
	}
})

/** 저장된 추천 코드를 DB에서 직접 읽는다. */
internal fun referralCodeOf(userId: Long): String? =
	IntegrationUtil.getQuery()
		.select(QUserEntity.userEntity.referralCode)
		.from(QUserEntity.userEntity)
		.where(QUserEntity.userEntity.id.eq(userId))
		.fetchOne()
