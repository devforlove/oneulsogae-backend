package com.org.oneulsogae.api.payments

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinHistoryEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemPurchaseEntity
import com.org.oneulsogae.infra.fixture.CoinItemEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.payments.command.entity.QIapPaymentEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /coins/v1/iap/purchases` E2E.
 * - SKU→coin_item 해석 후 적립 + iap_payments 기록.
 * - 같은 transaction_id 재검증: 재적립 없이 현재 잔액 재생.
 * - 1회 패키지: 다른 거래로 같은 상품 재구매 시 409.
 * - IAP 미판매(PG 전용) 상품 SKU: 400(PAYMENTS-007). (스텁 검증기가 토큰만 확인·통과하므로 채널 판정만 검증됨)
 */
class IapPurchaseE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QIapPaymentEntity.iapPaymentEntity)
		IntegrationUtil.deleteAll(QCoinItemPurchaseEntity.coinItemPurchaseEntity)
		IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
		IntegrationUtil.deleteAll(QCoinItemEntity.coinItemEntity)
	}

	fun balanceOf(userId: Long): Int? {
		val b = QCoinBalanceEntity.coinBalanceEntity
		return IntegrationUtil.getQuery().selectFrom(b).where(b.userId.eq(userId)).fetchOne()?.balance
	}

	fun iapCount(txId: String): Int {
		val p = QIapPaymentEntity.iapPaymentEntity
		return IntegrationUtil.getQuery().selectFrom(p).where(p.transactionId.eq(txId)).fetch().size
	}

	describe("POST /coins/v1/iap/purchases") {

		context("IAP 상품 영수증을 검증하면") {
			it("SKU로 상품을 찾아 적립하고 iap_payments를 남긴다") {
				val userId = 9301L
				IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 300, saleChannel = CoinSaleChannel.IAP, storeProductId = "sku.iap.a"),
				)

				post("/coins/v1/iap/purchases") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"platform":"IOS","productId":"sku.iap.a","purchaseToken":"tok-1","transactionId":"txn-1"}""")
				} expect {
					status(200)
					body("data.coinBalance", 300)
				}

				balanceOf(userId) shouldBe 300
				iapCount("txn-1") shouldBe 1
			}
		}

		context("같은 transactionId로 다시 검증하면") {
			it("재적립 없이 현재 잔액을 재생한다") {
				val userId = 9302L
				IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 300, saleChannel = CoinSaleChannel.IAP, storeProductId = "sku.iap.b"),
				)
				val body = """{"platform":"IOS","productId":"sku.iap.b","purchaseToken":"tok-2","transactionId":"txn-2"}"""

				post("/coins/v1/iap/purchases") { bearer(accessTokenFor(userId)); jsonBody(body) } expect { status(200) }
				post("/coins/v1/iap/purchases") {
					bearer(accessTokenFor(userId)); jsonBody(body)
				} expect {
					status(200)
					body("data.coinBalance", 300)
				}

				balanceOf(userId) shouldBe 300
				iapCount("txn-2") shouldBe 1
			}
		}

		context("1회 패키지를 다른 거래로 재구매하면") {
			it("409(PAYMENTS-006)") {
				val userId = 9303L
				IntegrationUtil.persist(
					CoinItemEntityFixture.create(
						coinAmount = 300, oncePerUser = true, saleChannel = CoinSaleChannel.IAP, storeProductId = "sku.iap.pkg",
					),
				)

				post("/coins/v1/iap/purchases") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"platform":"IOS","productId":"sku.iap.pkg","purchaseToken":"tok-3","transactionId":"txn-3a"}""")
				} expect { status(200) }

				post("/coins/v1/iap/purchases") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"platform":"IOS","productId":"sku.iap.pkg","purchaseToken":"tok-3","transactionId":"txn-3b"}""")
				} expect {
					status(409)
					body("error.code", "PAYMENTS-006")
				}

				balanceOf(userId) shouldBe 300
			}
		}

		context("IAP로 팔지 않는 PG 전용 상품 SKU면") {
			it("400(PAYMENTS-007)") {
				val userId = 9304L
				// PG 전용이지만 SKU를 붙여 저장(해석은 되나 채널 판정에서 거부되는지 검증).
				IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 100, saleChannel = CoinSaleChannel.PG, storeProductId = "sku.pgonly"),
				)

				post("/coins/v1/iap/purchases") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"platform":"IOS","productId":"sku.pgonly","purchaseToken":"tok-4","transactionId":"txn-4"}""")
				} expect {
					status(400)
					body("error.code", "PAYMENTS-007")
				}
			}
		}
	}
})
