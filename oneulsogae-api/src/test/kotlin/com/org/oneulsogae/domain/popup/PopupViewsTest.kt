package com.org.oneulsogae.domain.popup

import com.org.oneulsogae.common.popup.PopupType
import com.org.oneulsogae.core.popup.query.dto.PopupView
import com.org.oneulsogae.core.popup.query.dto.PopupViews
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [PopupViews] 일급 컬렉션 유닛 테스트.
 * 일일 보상(DAILY_REWARD) 팝업 포함 여부 판정([PopupViews.hasDailyReward])과
 * 한 건만 의미 있는 유형의 중복 제거([PopupViews.distinctSinglePerUserTypes])를 검증한다.
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

	describe("distinctSinglePerUserTypes") {
		it("일일 보상·신규 유저 팝업이 중복되면 앞선 한 건만 남긴다") {
			val result: PopupViews = PopupViews(
				listOf(
					view(1L, PopupType.DAILY_REWARD),
					view(2L, PopupType.NEW_USER),
					view(3L, PopupType.DAILY_REWARD),
					view(4L, PopupType.NEW_USER),
				),
			).distinctSinglePerUserTypes()

			result.values.map { it.id } shouldBe listOf(1L, 2L)
		}

		it("환불 팝업도 유형별로 한 건만 남긴다") {
			val result: PopupViews = PopupViews(
				listOf(
					view(1L, PopupType.MATCH_FAILED_REFUND),
					view(2L, PopupType.MATCH_FAILED_REFUND),
					view(3L, PopupType.MEETING_FAILED_REFUND),
					view(4L, PopupType.MEETING_FAILED_REFUND),
				),
			).distinctSinglePerUserTypes()

			// 유형이 다르면 함께 노출된다. 남은 건은 응답에서 빠져 제거되지 않으므로 다음 조회에 이어 노출된다.
			result.values.map { it.id } shouldBe listOf(1L, 3L)
			result.idsToRemoveAfterView() shouldBe listOf(1L, 3L)
		}

		it("일반 공지는 여러 건이어도 그대로 둔다") {
			val result: PopupViews = PopupViews(
				listOf(
					view(1L, PopupType.NORMAL),
					view(2L, PopupType.NORMAL),
					view(3L, PopupType.NORMAL),
				),
			).distinctSinglePerUserTypes()

			result.values.map { it.id } shouldBe listOf(1L, 2L, 3L)
		}

		it("중복을 줄여도 나머지 팝업의 순서는 그대로다") {
			val result: PopupViews = PopupViews(
				listOf(
					view(1L, PopupType.DAILY_REWARD),
					view(2L, PopupType.NORMAL),
					view(3L, PopupType.DAILY_REWARD),
					view(4L, PopupType.NORMAL),
				),
			).distinctSinglePerUserTypes()

			result.values.map { it.id } shouldBe listOf(1L, 2L, 4L)
		}

		it("빈 목록이면 그대로 빈 목록이다") {
			PopupViews.empty().distinctSinglePerUserTypes().isEmpty() shouldBe true
		}
	}
})
