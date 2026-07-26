package com.org.oneulsogae.api.payments

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinHistoryEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemPurchaseEntity
import com.org.oneulsogae.infra.fixture.CoinItemEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.payments.command.entity.QCoinPaymentEntity
import io.kotest.matchers.shouldBe

/**
 * PG 코인 결제완료의 1회 패키지 제한 E2E.
 * - once_per_user 상품 최초 구매: 적립 + coin_item_purchases 가드 기록.
 * - 2회차: 409(PAYMENTS-006), 재적립 없음.
 */
class CoinCompleteOncePackageE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QCoinItemPurchaseEntity.coinItemPurchaseEntity)
		IntegrationUtil.deleteAll(QCoinPaymentEntity.coinPaymentEntity)
		IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
		IntegrationUtil.deleteAll(QCoinItemEntity.coinItemEntity)
	}

	fun purchaseGuardCount(userId: Long, itemId: Long): Int {
		val p = QCoinItemPurchaseEntity.coinItemPurchaseEntity
		return IntegrationUtil.getQuery().selectFrom(p)
			.where(p.userId.eq(userId), p.itemId.eq(itemId)).fetch().size
	}

	fun balanceOf(userId: Long): Int? {
		val b = QCoinBalanceEntity.coinBalanceEntity
		return IntegrationUtil.getQuery().selectFrom(b).where(b.userId.eq(userId)).fetchOne()?.balance
	}

	describe("POST /payments/v1/coin/complete (1회 패키지)") {

		context("once_per_user 패키지를 처음 구매하면") {
			it("적립하고 구매 가드를 남긴다") {
				val userId = 9201L
				val itemId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 300, price = 30000, salePrice = 19900, oncePerUser = true),
				).id!!

				post("/payments/v1/coin/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"itemId": $itemId, "paymentKey": "pkg_key_1", "orderId": "ord_pkg_1"}""")
				} expect {
					status(200)
					body("data.coinAmount", 300)
					body("data.balance", 300)
				}

				purchaseGuardCount(userId, itemId) shouldBe 1
				balanceOf(userId) shouldBe 300
			}
		}

		context("이미 구매한 패키지를 다시 구매하면") {
			it("409(PAYMENTS-006)이고 재적립되지 않는다") {
				val userId = 9202L
				val itemId = IntegrationUtil.persist(
					CoinItemEntityFixture.create(coinAmount = 300, price = 30000, salePrice = 19900, oncePerUser = true),
				).id!!

				post("/payments/v1/coin/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"itemId": $itemId, "paymentKey": "pkg_key_2", "orderId": "ord_pkg_2"}""")
				} expect { status(200) }

				// 2회차: 다른 paymentKey(새 PG 인증)로 재시도 → 선검사에서 막힌다.
				post("/payments/v1/coin/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"itemId": $itemId, "paymentKey": "pkg_key_2b", "orderId": "ord_pkg_2b"}""")
				} expect {
					status(409)
					body("error.code", "PAYMENTS-006")
				}

				purchaseGuardCount(userId, itemId) shouldBe 1
				balanceOf(userId) shouldBe 300
			}
		}
	}
})
