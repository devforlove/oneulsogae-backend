package com.org.oneulsogae.api.coin

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemEntity
import com.org.oneulsogae.infra.fixture.CoinItemEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue

/**
 * 코인샵 기간 한정 오퍼 E2E 테스트.
 * 만료(validDays=0)·활성(validDays=30) 오퍼가 상점 목록 노출과 체크아웃 결제-전 게이트에서 어떻게 처리되는지 검증한다.
 * (실시각 기준: 방금 가입한 유저는 validDays=30이면 활성, validDays=0이면 즉시 만료)
 */
class CoinTimeLimitedOfferE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QCoinItemEntity.coinItemEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}

	describe("GET /coins/v1/shop (기간 한정 오퍼)") {

		context("만료된 기간 한정 상품과 상시 상품이 섞여 있으면") {
			it("만료 상품은 목록에서 빠지고 상시 상품은 남는다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "offer-shop-1")).id!!
				val alwaysId: Long = IntegrationUtil.persist(CoinItemEntityFixture.create(coinAmount = 100)).id!!
				val expiredId: Long = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 200, validDays = 0),
				).id!!

				get("/coins/v1/shop?channel=PG") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.id", hasItem(alwaysId.toInt()))
					body("data.id", not(hasItem(expiredId.toInt())))
					body("data[0].offerExpiresAt", nullValue())
				}
			}
		}

		context("활성 기간 한정 상품은") {
			it("목록에 노출된다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "offer-shop-2")).id!!
				val activeId: Long = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 200, validDays = 30),
				).id!!

				get("/coins/v1/shop?channel=PG") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.id", hasItem(activeId.toInt()))
					body("data[0].offerExpiresAt", notNullValue())
				}
			}
		}
	}

	describe("GET /payments/v1/coin/checkout (기간 한정 오퍼)") {

		context("만료된 기간 한정 상품으로 체크아웃하면") {
			it("400 COIN-005를 반환한다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "offer-checkout-1")).id!!
				val expiredId: Long = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 200, validDays = 0),
				).id!!

				get("/payments/v1/coin/checkout?itemId=$expiredId") {
					bearer(accessTokenFor(userId))
				} expect {
					status(400)
					body("success", false)
					body("error.code", "COIN-005")
				}
			}
		}

		context("활성 기간 한정 상품으로 체크아웃하면") {
			it("200으로 아이템을 반환한다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "offer-checkout-2")).id!!
				val activeId: Long = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 200, validDays = 30),
				).id!!

				get("/payments/v1/coin/checkout?itemId=$activeId") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.item.id", activeId.toInt())
					body("data.item.coinAmount", 200)
				}
			}
		}
	}
})
