package com.org.oneulsogae.api.payments

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.common.config.FakePaymentGateway
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.core.payments.command.domain.PaymentStatus
import com.org.oneulsogae.infra.coin.command.entity.CoinItemEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinHistoryEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinItemEntity
import com.org.oneulsogae.infra.fixture.CoinBalanceEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.payments.command.entity.CoinPaymentEntity
import com.org.oneulsogae.infra.payments.command.entity.QCoinPaymentEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /payments/v1/coin/complete` E2E 테스트.
 *
 * 코인 구매 결제완료를 접수한다: PENDING 결제 기록을 선저장하고 PG 최종 승인(confirm)을 거쳐,
 * 성공 시 구매한 코인을 즉시 잔액에 적립(모임 좌석과 달리 운영자 승인 없이 즉시 지급)한다.
 * - 실결제가는 서버가 상품 할인가(salePrice)로 확정한다.
 * - PG 승인 실패 402(PAYMENTS-004): 코인 미지급, 결제 기록은 FAILED로 보존.
 * - 없는 itemId면 404(COIN-004).
 */
class CoinCompleteE2ETest : AbstractIntegrationSupport({

	// 100코인, 정가 12000, 할인가 10000 코인 상품을 저장하고 itemId를 돌려준다.
	fun persistCoinItem(coinAmount: Int = 100, price: Int = 12000, salePrice: Int = 10000): Long =
		IntegrationUtil.persist(CoinItemEntity(coinAmount = coinAmount, price = price, salePrice = salePrice)).id!!

	fun findCoinPayment(userId: Long): CoinPaymentEntity? {
		val payment: QCoinPaymentEntity = QCoinPaymentEntity.coinPaymentEntity
		return IntegrationUtil.getQuery().selectFrom(payment).where(payment.userId.eq(userId)).fetchOne()
	}

	fun balanceOf(userId: Long): Int? {
		val balance: QCoinBalanceEntity = QCoinBalanceEntity.coinBalanceEntity
		return IntegrationUtil.getQuery().selectFrom(balance).where(balance.userId.eq(userId)).fetchOne()?.balance
	}

	fun purchaseHistoryCountOf(userId: Long): Int {
		val history: QCoinHistoryEntity = QCoinHistoryEntity.coinHistoryEntity
		return IntegrationUtil.getQuery().selectFrom(history)
			.where(history.userId.eq(userId), history.coinGetType.eq(CoinGetType.PURCHASE))
			.fetch().size
	}

	describe("POST /payments/v1/coin/complete") {

		context("PG 승인이 성공하면") {
			it("서버 확정가로 결제하고 코인을 즉시 적립하며 결제 기록을 APPROVED로 남긴다") {
				val userId: Long = 8001L
				val itemId: Long = persistCoinItem()

				post("/payments/v1/coin/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"itemId": $itemId, "paymentKey": "coin_key_1", "orderId": "ord_coin_key_1"}""")
				} expect {
					status(200)
					body("success", true)
					body("data.amount", 10000)
					body("data.coinAmount", 100)
					body("data.balance", 100)
				}

				val payment: CoinPaymentEntity? = findCoinPayment(userId)
				payment?.itemId shouldBe itemId
				payment?.coinAmount shouldBe 100
				payment?.amount shouldBe 10000
				payment?.paymentKey shouldBe "coin_key_1"
				payment?.orderId shouldBe "ord_coin_key_1"
				payment?.status shouldBe PaymentStatus.APPROVED

				balanceOf(userId) shouldBe 100
				purchaseHistoryCountOf(userId) shouldBe 1
			}
		}

		context("이미 잔액이 있는 사용자가 결제완료하면") {
			it("기존 잔액에 구매 코인을 더한다") {
				val userId: Long = 8002L
				val itemId: Long = persistCoinItem()
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = userId, balance = 30))

				post("/payments/v1/coin/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"itemId": $itemId, "paymentKey": "coin_key_2", "orderId": "ord_coin_key_2"}""")
				} expect {
					status(200)
					body("data.balance", 130)
				}

				balanceOf(userId) shouldBe 130
			}
		}

		context("PG 승인(confirm)이 실패하면") {
			it("402 PAYMENTS-004를 반환하고 코인을 지급하지 않으며 결제 기록을 FAILED로 남긴다") {
				val userId: Long = 8003L
				val itemId: Long = persistCoinItem()
				FakePaymentGateway.result = FakePaymentGateway.REJECTED

				post("/payments/v1/coin/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"itemId": $itemId, "paymentKey": "coin_key_fail", "orderId": "ord_coin_key_fail"}""")
				} expect {
					status(402)
					body("error.code", "PAYMENTS-004")
				}

				val payment: CoinPaymentEntity? = findCoinPayment(userId)
				payment?.status shouldBe PaymentStatus.FAILED
				payment?.paymentKey shouldBe "coin_key_fail"
				payment?.failReason shouldBe FakePaymentGateway.REJECTED.failReason

				// 코인 미지급: 잔액 행·적립 이력이 생기지 않는다.
				balanceOf(userId) shouldBe null
				purchaseHistoryCountOf(userId) shouldBe 0
			}
		}

		context("없는 itemId로 결제완료하면") {
			it("404 COIN-004를 반환하고 아무것도 저장하지 않는다") {
				val userId: Long = 8004L

				post("/payments/v1/coin/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"itemId": 999999, "paymentKey": "coin_key_nf", "orderId": "ord_coin_key_nf"}""")
				} expect {
					status(404)
					body("error.code", "COIN-004")
				}

				findCoinPayment(userId) shouldBe null
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QCoinPaymentEntity.coinPaymentEntity)
		IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
		IntegrationUtil.deleteAll(QCoinItemEntity.coinItemEntity)
	}
})
