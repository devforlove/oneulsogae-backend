package com.org.meeple.domain.popup

import com.org.meeple.common.popup.PopupType
import com.org.meeple.core.popup.query.dto.PopupView
import com.org.meeple.core.popup.query.dto.PopupViews
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [PopupViews] 일급 컬렉션 유닛 테스트.
 * 일일 보상(DAILY_REWARD) 팝업 포함 여부 판정([PopupViews.hasDailyReward])을 검증한다.
 */
class PopupViewsTest : DescribeSpec({

	fun view(id: Long, popUpType: PopupType): PopupView =
		PopupView(
			id = id,
			title = "title-$id",
			description = "description-$id",
			displayOrder = 0,
			imageUrl = null,
			imageWidth = null,
			imageHeight = null,
			linkUrl = null,
			buttonText = null,
			popUpType = popUpType,
		)

	describe("hasDailyReward") {
		it("일일 보상 팝업이 있으면 true") {
			PopupViews(listOf(view(1L, PopupType.NORMAL), view(2L, PopupType.DAILY_REWARD)))
				.hasDailyReward() shouldBe true
		}

		it("일일 보상 팝업이 없으면 false") {
			PopupViews(listOf(view(1L, PopupType.NORMAL), view(2L, PopupType.MATCH_FAILED_REFUND)))
				.hasDailyReward() shouldBe false
		}

		it("빈 목록이면 false") {
			PopupViews.empty().hasDailyReward() shouldBe false
		}
	}

	describe("withoutDailyReward") {
		it("일일 보상 팝업만 제외하고 나머지는 유지한다") {
			val result: PopupViews = PopupViews(
				listOf(
					view(1L, PopupType.NORMAL),
					view(2L, PopupType.DAILY_REWARD),
					view(3L, PopupType.MATCH_FAILED_REFUND),
				),
			).withoutDailyReward()

			result.values.map { it.id } shouldBe listOf(1L, 3L)
			result.hasDailyReward() shouldBe false
		}

		it("일일 보상 팝업이 없으면 그대로 유지한다") {
			PopupViews(listOf(view(1L, PopupType.NORMAL)))
				.withoutDailyReward().values.map { it.id } shouldBe listOf(1L)
		}
	}

	describe("withoutNewUser") {
		it("신규 유저 팝업만 제외하고 나머지는 유지한다") {
			val result: PopupViews = PopupViews(
				listOf(
					view(1L, PopupType.NORMAL),
					view(2L, PopupType.NEW_USER),
					view(3L, PopupType.DAILY_REWARD),
				),
			).withoutNewUser()

			result.values.map { it.id } shouldBe listOf(1L, 3L)
		}

		it("신규 유저 팝업이 없으면 그대로 유지한다") {
			PopupViews(listOf(view(1L, PopupType.NORMAL)))
				.withoutNewUser().values.map { it.id } shouldBe listOf(1L)
		}
	}
})
