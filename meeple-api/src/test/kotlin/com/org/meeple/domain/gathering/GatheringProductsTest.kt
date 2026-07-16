package com.org.meeple.domain.gathering

import com.org.meeple.admin.gathering.command.domain.GatheringFee
import com.org.meeple.admin.gathering.command.domain.GatheringProduct
import com.org.meeple.admin.gathering.command.domain.GatheringProducts
import com.org.meeple.common.gathering.GatheringProductType
import com.org.meeple.common.user.Gender
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class GatheringProductsTest : DescribeSpec({

	val fee: GatheringFee = GatheringFee(male = 10000, female = 8000)

	describe("GatheringProducts.create") {

		it("정상가만 있으면 남/녀 NORMAL 2행을 만든다") {
			val products: GatheringProducts = GatheringProducts.create(
				gatheringId = 1L,
				fee = fee,
				earlyBirdDiscountRate = null,
				discountFee = null,
			)

			products.values.map { product: GatheringProduct -> Triple(product.gender, product.type, product.price) }
				.shouldContainExactlyInAnyOrder(
					Triple(Gender.MALE, GatheringProductType.NORMAL, 10000),
					Triple(Gender.FEMALE, GatheringProductType.NORMAL, 8000),
				)
		}

		it("얼리버드 할인율이 있으면 정가 × (100 - 율) / 100 버림 가격의 EARLY_BIRD 행을 추가한다") {
			val products: GatheringProducts = GatheringProducts.create(
				gatheringId = 1L,
				fee = fee,
				earlyBirdDiscountRate = 30,
				discountFee = null,
			)

			products.values.map { product: GatheringProduct -> Triple(product.gender, product.type, product.price) }
				.shouldContainExactlyInAnyOrder(
					Triple(Gender.MALE, GatheringProductType.NORMAL, 10000),
					Triple(Gender.FEMALE, GatheringProductType.NORMAL, 8000),
					Triple(Gender.MALE, GatheringProductType.EARLY_BIRD, 7000),
					Triple(Gender.FEMALE, GatheringProductType.EARLY_BIRD, 5600),
				)
		}

		it("얼리버드 가격은 버림으로 계산한다") {
			val products: GatheringProducts = GatheringProducts.create(
				gatheringId = 1L,
				fee = GatheringFee(male = 9999, female = 9999),
				earlyBirdDiscountRate = 13,
				discountFee = null,
			)

			// 9999 × 87 / 100 = 8699.13 → 8699
			products.values.first { product: GatheringProduct -> product.type == GatheringProductType.EARLY_BIRD }
				.price shouldBe 8699
		}

		it("할인가가 있으면 남/녀 DISCOUNT 행을 추가한다") {
			val products: GatheringProducts = GatheringProducts.create(
				gatheringId = 1L,
				fee = fee,
				earlyBirdDiscountRate = 30,
				discountFee = GatheringFee(male = 9000, female = 7000),
			)

			products.values.size shouldBe 6
			products.values.filter { product: GatheringProduct -> product.type == GatheringProductType.DISCOUNT }
				.map { product: GatheringProduct -> product.gender to product.price }
				.shouldContainExactlyInAnyOrder(
					Gender.MALE to 9000,
					Gender.FEMALE to 7000,
				)
		}
	}

	describe("withScheduleId") {

		it("모든 행에 scheduleId를 채운 새 컬렉션을 돌려준다") {
			val products: GatheringProducts = GatheringProducts.create(
				gatheringId = 1L,
				fee = fee,
				earlyBirdDiscountRate = null,
				discountFee = null,
			)

			val assigned: GatheringProducts = products.withScheduleId(77L)

			assigned.values.all { product: GatheringProduct -> product.scheduleId == 77L } shouldBe true
			// 원본은 변경되지 않는다.
			products.values.all { product: GatheringProduct -> product.scheduleId == 0L } shouldBe true
		}
	}
})
