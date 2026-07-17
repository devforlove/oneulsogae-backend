package com.org.meeple.api.payments

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.infra.coin.command.entity.CoinItemEntity
import com.org.meeple.infra.coin.command.entity.QCoinItemEntity
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.PaymentMethodEntityFixture
import com.org.meeple.infra.payments.command.entity.QPaymentMethodEntity
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize

/**
 * `GET /payments/v1/coin/checkout` E2E 테스트.
 *
 * 코인 구매 직전 체크아웃 화면 데이터를 조회한다: 구매하려는 코인 아이템(itemId) + 구매방법(활성 결제수단)을 내려준다.
 * - 아이템은 요청 itemId의 가격 필드(정가·할인가·코인개수·1개당가·할인율)를 그대로 반환한다.
 * - 구매방법은 활성 결제수단만 노출 순서(displayOrder asc)로, 비활성은 제외한다.
 * - 없는 itemId면 404(COIN-004).
 */
class CoinCheckoutE2ETest : AbstractIntegrationSupport({

	describe("GET /payments/v1/coin/checkout") {

		context("존재하는 코인 아이템으로 체크아웃을 조회하면") {
			it("아이템 정보와 활성 구매방법(노출 순서)을 반환한다") {
				// 100코인, 정가 12000, 할인가 10000 → 1개당 100원, 할인율 round((12000-10000)/12000*100)=17.
				val itemId: Long = IntegrationUtil.persist(
					CoinItemEntity(coinAmount = 100, price = 12000, salePrice = 10000),
				).id!!
				// 활성 2건(역순 저장으로 정렬 검증) + 비활성 1건(제외 검증).
				IntegrationUtil.persist(PaymentMethodEntityFixture.create(code = "CARD", name = "카드", displayOrder = 2))
				IntegrationUtil.persist(PaymentMethodEntityFixture.create(code = "BANK_TRANSFER", name = "무통장입금", displayOrder = 1))
				IntegrationUtil.persist(PaymentMethodEntityFixture.create(code = "KAKAO_PAY", name = "카카오페이", displayOrder = 3, active = false))

				get("/payments/v1/coin/checkout?itemId=$itemId") {
					bearer(accessTokenFor(9001L))
				} expect {
					status(200)
					body("success", true)
					body("data.item.id", itemId.toInt())
					body("data.item.coinAmount", 100)
					body("data.item.price", 12000)
					body("data.item.salePrice", 10000)
					body("data.item.pricePerCoin", 100)
					body("data.item.discountRate", 17)
					body("data.paymentMethods", hasSize<Any>(2))
					body("data.paymentMethods.code", contains("BANK_TRANSFER", "CARD"))
					body("data.paymentMethods.name", contains("무통장입금", "카드"))
				}
			}
		}

		context("존재하지 않는 itemId로 체크아웃을 조회하면") {
			it("404 COIN-004를 반환한다") {
				get("/payments/v1/coin/checkout?itemId=999999") {
					bearer(accessTokenFor(9002L))
				} expect {
					status(404)
					body("success", false)
					body("error.code", "COIN-004")
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QCoinItemEntity.coinItemEntity)
		IntegrationUtil.deleteAll(QPaymentMethodEntity.paymentMethodEntity)
	}
})
