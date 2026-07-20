package com.org.oneulsogae.domain.gathering

import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.gathering.GatheringScheduleStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.gathering.query.dto.GatheringProductView
import com.org.oneulsogae.core.gathering.query.dto.GatheringScheduleView
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class GatheringScheduleViewTest : DescribeSpec({

	fun view(products: List<GatheringProductView>): GatheringScheduleView =
		GatheringScheduleView(
			id = 1L,
			startAt = LocalDateTime.of(2999, 1, 1, 19, 0, 0),
			endAt = null,
			maleRemaining = 4,
			femaleRemaining = 4,
			earlyBirdCapacity = null,
			earlyBirdRemaining = null,
			status = GatheringScheduleStatus.SCHEDULED,
			products = products,
		)

	describe("productIdFor") {

		// 남성은 3티어 모두, 여성은 NORMAL만 가진 상품 구성.
		val products: List<GatheringProductView> = listOf(
			GatheringProductView(id = 11L, gender = Gender.MALE, type = GatheringProductType.NORMAL, price = 10000),
			GatheringProductView(id = 12L, gender = Gender.FEMALE, type = GatheringProductType.NORMAL, price = 8000),
			GatheringProductView(id = 13L, gender = Gender.MALE, type = GatheringProductType.EARLY_BIRD, price = 7000),
			GatheringProductView(id = 14L, gender = Gender.MALE, type = GatheringProductType.DISCOUNT, price = 9000),
		)

		it("얼리버드가 유효하면 EARLY_BIRD 상품의 id를 돌려준다") {
			val target: GatheringScheduleView = view(products).copy(earlyBirdCapacity = 5, earlyBirdRemaining = 2)

			target.productIdFor(Gender.MALE) shouldBe 13L
			// 실결제가와 같은 행을 가리킨다.
			target.salePriceFor(Gender.MALE) shouldBe 7000
		}

		it("얼리버드가 유효해도 해당 성별 EARLY_BIRD 상품이 없으면 NORMAL 상품의 id를 돌려준다") {
			val target: GatheringScheduleView = view(products).copy(earlyBirdCapacity = 5, earlyBirdRemaining = 2)

			target.productIdFor(Gender.FEMALE) shouldBe 12L
			target.salePriceFor(Gender.FEMALE) shouldBe 8000
		}

		it("얼리버드가 소진되고 할인가가 있으면 DISCOUNT 상품의 id를 돌려준다") {
			val target: GatheringScheduleView = view(products).copy(earlyBirdCapacity = 5, earlyBirdRemaining = 0)

			target.productIdFor(Gender.MALE) shouldBe 14L
			target.salePriceFor(Gender.MALE) shouldBe 9000
		}

		it("얼리버드가 소진되고 할인가가 없으면 NORMAL 상품의 id를 돌려준다") {
			val target: GatheringScheduleView = view(products).copy(earlyBirdCapacity = 5, earlyBirdRemaining = 0)

			target.productIdFor(Gender.FEMALE) shouldBe 12L
		}

		it("얼리버드 티어가 없으면 EARLY_BIRD 행이 있어도 NORMAL 상품의 id를 돌려준다") {
			view(products).productIdFor(Gender.MALE) shouldBe 11L
		}

		it("해당 성별 NORMAL 상품이 없으면 실패한다") {
			val target: GatheringScheduleView = view(
				listOf(
					GatheringProductView(id = 11L, gender = Gender.MALE, type = GatheringProductType.NORMAL, price = 10000),
				),
			)

			shouldThrow<IllegalStateException> { target.productIdFor(Gender.FEMALE) }
		}
	}
})
