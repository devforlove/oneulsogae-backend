package com.org.oneulsogae.api.coin

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.infra.coin.command.entity.CoinItemPurchaseEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemPurchaseEntity
import com.org.oneulsogae.infra.fixture.CoinItemEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not

/**
 * `GET /coins/v1/shop?channel=` E2E 테스트.
 * - 채널 필터: IAP 요청 시 PG 전용 상품 제외, IAP·BOTH 포함.
 * - 1회 패키지: 이미 구매한 once_per_user 상품은 목록에서 숨김.
 * 상점 조회는 order-by가 없어 순서 대신 집합 멤버십(hasItem)으로 단언한다.
 * (id는 RestAssured가 Int로 역직렬화하므로 matcher에 Int로 넣는다)
 */
class CoinShopE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QCoinItemPurchaseEntity.coinItemPurchaseEntity)
		IntegrationUtil.deleteAll(QCoinItemEntity.coinItemEntity)
	}

	describe("GET /coins/v1/shop") {

		context("channel=IAP로 조회하면") {
			it("PG 전용 상품은 빠지고 IAP·BOTH 상품만 내려간다") {
				val userId = 9101L
				IntegrationUtil.persist(CoinItemEntityFixture.create(coinAmount = 100, saleChannel = CoinSaleChannel.PG))
				val iapId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 300, saleChannel = CoinSaleChannel.IAP, storeProductId = "sku.iap.300"),
				).id!!
				val bothId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 500, saleChannel = CoinSaleChannel.BOTH, storeProductId = "sku.both.500"),
				).id!!

				val pgOnlyId = IntegrationUtil.persist(CoinItemEntityFixture.create(coinAmount = 100, saleChannel = CoinSaleChannel.PG)).id!!

				get("/coins/v1/shop?channel=IAP") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.size()", 2)
					body("data.id", hasItem(iapId.toInt()))
					body("data.id", hasItem(bothId.toInt()))
					body("data.id", not(hasItem(pgOnlyId.toInt())))
				}
			}
		}

		context("channel=PG로 조회하면") {
			it("IAP 전용 상품은 빠지고 PG·BOTH 상품만 내려간다") {
				val userId = 9102L
				val pgId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 100, saleChannel = CoinSaleChannel.PG),
				).id!!
				val iapOnlyId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 300, saleChannel = CoinSaleChannel.IAP, storeProductId = "sku.iap.301"),
				).id!!
				val bothId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 500, saleChannel = CoinSaleChannel.BOTH, storeProductId = "sku.both.501"),
				).id!!

				get("/coins/v1/shop?channel=PG") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.size()", 2)
					body("data.id", hasItem(pgId.toInt()))
					body("data.id", hasItem(bothId.toInt()))
					body("data.id", not(hasItem(iapOnlyId.toInt())))
				}
			}
		}

		context("이미 구매한 1회 패키지가 있으면") {
			it("그 사용자에게는 목록에서 숨겨진다 (일반 상품은 유지)") {
				val userId = 9103L
				val normalId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 100, saleChannel = CoinSaleChannel.PG),
				).id!!
				val packageId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 300, oncePerUser = true, saleChannel = CoinSaleChannel.PG),
				).id!!
				// 이 사용자가 패키지를 이미 구매한 상태로 만든다.
				IntegrationUtil.persist(CoinItemPurchaseEntity(userId = userId, itemId = packageId))

				get("/coins/v1/shop?channel=PG") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.size()", 1)
					body("data.id", hasItem(normalId.toInt()))
					body("data.id", not(hasItem(packageId.toInt())))
				}
			}
		}

		context("1회 패키지를 아직 안 산 다른 사용자면") {
			it("패키지가 목록에 보이고 oncePerUser=true다") {
				val buyerId = 9104L
				val otherId = 9105L
				val packageId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 300, oncePerUser = true, saleChannel = CoinSaleChannel.PG),
				).id!!
				IntegrationUtil.persist(CoinItemPurchaseEntity(userId = buyerId, itemId = packageId))

				get("/coins/v1/shop?channel=PG") {
					bearer(accessTokenFor(otherId))
				} expect {
					status(200)
					body("data.size()", 1)
					body("data[0].id", packageId.toInt())
					body("data[0].oncePerUser", true)
				}
			}
		}
	}
})
