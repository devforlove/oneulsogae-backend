package com.org.oneulsogae.domain.coin

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

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

	describe("isOfferActiveAt") {
		val createdAt: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

		it("validDays가 null이면 언제나 활성이다(상시 상품)") {
			val item: CoinItem = CoinItem.create(
				coinAmount = 100, price = 12000, salePrice = 10000, validDays = null,
			)
			item.isOfferActiveAt(createdAt, LocalDateTime.of(2999, 1, 1, 0, 0)) shouldBe true
		}

		it("가입시각 + validDays일 직전이면 활성이다") {
			val item: CoinItem = CoinItem.create(
				coinAmount = 200, price = 10000, salePrice = 4900, oncePerUser = true, validDays = 7,
			)
			item.isOfferActiveAt(createdAt, LocalDateTime.of(2026, 1, 7, 23, 59)) shouldBe true
		}

		it("가입시각 + validDays일 정각이면 만료다(exclusive 경계)") {
			val item: CoinItem = CoinItem.create(
				coinAmount = 200, price = 10000, salePrice = 4900, oncePerUser = true, validDays = 7,
			)
			item.isOfferActiveAt(createdAt, LocalDateTime.of(2026, 1, 8, 0, 0)) shouldBe false
		}

		it("가입시각 + validDays일 이후면 만료다") {
			val item: CoinItem = CoinItem.create(
				coinAmount = 200, price = 10000, salePrice = 4900, oncePerUser = true, validDays = 7,
			)
			item.isOfferActiveAt(createdAt, LocalDateTime.of(2026, 1, 9, 0, 0)) shouldBe false
		}
	}
})
