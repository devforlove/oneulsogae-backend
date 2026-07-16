package com.org.meeple.domain.gathering

import com.org.meeple.common.gathering.GatheringProductType
import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.gathering.query.dto.GatheringProductView
import com.org.meeple.core.gathering.query.dto.GatheringScheduleView
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

		it("해당 성별 NORMAL 상품의 id를 돌려준다") {
			val target: GatheringScheduleView = view(
				listOf(
					GatheringProductView(id = 11L, gender = Gender.MALE, type = GatheringProductType.NORMAL, price = 10000),
					GatheringProductView(id = 12L, gender = Gender.FEMALE, type = GatheringProductType.NORMAL, price = 8000),
					GatheringProductView(id = 13L, gender = Gender.MALE, type = GatheringProductType.EARLY_BIRD, price = 7000),
				),
			)

			target.productIdFor(Gender.MALE) shouldBe 11L
			target.productIdFor(Gender.FEMALE) shouldBe 12L
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
