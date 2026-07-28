package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.coin.CoinPolicy
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.ReferralRewardGrantEntityFixture
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

		context("내 추천으로 보상이 지급된 친구가 2명이면") {
			it("추천 실적은 2명과 지급된 코인 합계다 (내가 추천받아 받은 보상은 빼고)") {
				val referrerId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "referral-summary-referrer", status = UserStatus.ACTIVE),
				).id!!
				val inviterId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "referral-summary-inviter", status = UserStatus.ACTIVE),
				).id!!

				// 내가 추천인인 지급 2건.
				(1..2).forEach { index: Int ->
					IntegrationUtil.persist(
						ReferralRewardGrantEntityFixture.create(
							referredDiHash = "summary-friend-$index",
							referrerUserId = referrerId,
							referredUserId = 9000L + index,
						),
					)
				}
				// 내가 남의 코드를 입력해 받은 건(추천인은 inviter)은 내 실적에 잡히지 않는다.
				IntegrationUtil.persist(
					ReferralRewardGrantEntityFixture.create(
						referredDiHash = "summary-me",
						referrerUserId = inviterId,
						referredUserId = referrerId,
					),
				)

				get("/users/v1/me/referral-code") {
					bearer(accessTokenFor(referrerId))
				} expect {
					status(200)
					body("data.referredUserCount", 2)
					body("data.earnedCoinAmount", CoinPolicy.REFERRAL_REWARD_COIN_AMOUNT * 2)
				}
			}
		}

		context("보상이 지급된 친구가 탈퇴해도") {
			it("이미 받은 실적은 그대로 남는다") {
				val referrerId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "referral-summary-withdrawn", status = UserStatus.ACTIVE),
				).id!!
				// 피추천인 계정 자체가 없어도(탈퇴·파기) 지급 이력은 남아 실적에 잡힌다.
				IntegrationUtil.persist(
					ReferralRewardGrantEntityFixture.create(
						referredDiHash = "summary-withdrawn-friend",
						referrerUserId = referrerId,
						referredUserId = 9500L,
					),
				)

				get("/users/v1/me/referral-code") {
					bearer(accessTokenFor(referrerId))
				} expect {
					status(200)
					body("data.referredUserCount", 1)
					body("data.earnedCoinAmount", CoinPolicy.REFERRAL_REWARD_COIN_AMOUNT)
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
