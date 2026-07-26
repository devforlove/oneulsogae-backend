package com.org.oneulsogae.domain.coin

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [CoinItem] 생성 규칙 유닛 테스트.
 * 판매 채널·1회 제한 필드와 불변식(IAP/BOTH 상품은 store_product_id 필수)을 검증한다.
 */
class CoinItemTest : DescribeSpec({

	describe("create") {
		it("PG 전용 상품은 store_product_id 없이 만들 수 있다") {
			val item: CoinItem = CoinItem.create(
				coinAmount = 100, price = 12000, salePrice = 10000,
				oncePerUser = false, saleChannel = CoinSaleChannel.PG, storeProductId = null,
			)
			item.saleChannel shouldBe CoinSaleChannel.PG
			item.oncePerUser shouldBe false
			item.storeProductId shouldBe null
		}

		it("IAP 상품은 store_product_id가 있으면 만들 수 있다") {
			val item: CoinItem = CoinItem.create(
				coinAmount = 300, price = 30000, salePrice = 19900,
				oncePerUser = true, saleChannel = CoinSaleChannel.IAP, storeProductId = "com.oneulsogae.coins.welcome",
			)
			item.oncePerUser shouldBe true
			item.storeProductId shouldBe "com.oneulsogae.coins.welcome"
		}

		it("IAP 상품인데 store_product_id가 없으면 예외를 던진다") {
			shouldThrow<IllegalArgumentException> {
				CoinItem.create(
					coinAmount = 300, price = 30000, salePrice = 19900,
					oncePerUser = true, saleChannel = CoinSaleChannel.IAP, storeProductId = null,
				)
			}
		}

		it("BOTH 상품인데 store_product_id가 없으면 예외를 던진다") {
			shouldThrow<IllegalArgumentException> {
				CoinItem.create(
					coinAmount = 300, price = 30000, salePrice = 19900,
					oncePerUser = false, saleChannel = CoinSaleChannel.BOTH, storeProductId = "  ",
				)
			}
		}
	}
})
