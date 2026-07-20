package com.org.oneulsogae.domain.gathering

import com.org.oneulsogae.common.gathering.GatheringType
import com.org.oneulsogae.core.gathering.query.dto.GatheringView
import com.org.oneulsogae.core.gathering.query.dto.GatheringViews
import com.org.oneulsogae.core.gathering.query.dto.GroupedGatherings
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class GatheringViewsTest : DescribeSpec({

	fun view(id: Long, type: GatheringType): GatheringView =
		GatheringView(
			id = id,
			type = type,
			title = "모임-$id",
			imageKey = null,
			region = "서울 강남구",
		)

	describe("GatheringViews.groupByType") {

		it("전달한 타입 선언 순서대로 그룹을 만든다") {
			val views: GatheringViews = GatheringViews(
				listOf(view(1L, GatheringType.PARTY), view(2L, GatheringType.COOKING)),
			)

			val grouped: GroupedGatherings = views.groupByType(GatheringType.entries)

			grouped.values.map { it.type } shouldBe listOf(
				GatheringType.ONE_ON_ONE_ROTATION,
				GatheringType.COOKING,
				GatheringType.PARTY,
			)
			grouped.values[0].typeDescription shouldBe "1:1 로테이션"
		}

		it("해당 타입 모임이 없으면 빈 배열 그룹으로 포함한다") {
			val views: GatheringViews = GatheringViews(listOf(view(1L, GatheringType.COOKING)))

			val grouped: GroupedGatherings = views.groupByType(GatheringType.entries)

			grouped.values.first { it.type == GatheringType.ONE_ON_ONE_ROTATION }.gatherings shouldBe emptyList()
			grouped.values.first { it.type == GatheringType.PARTY }.gatherings shouldBe emptyList()
			grouped.values.first { it.type == GatheringType.COOKING }.gatherings.map { it.id } shouldBe listOf(1L)
		}

		it("그룹 내에서 원래(입력) 순서를 유지한다") {
			val views: GatheringViews = GatheringViews(
				listOf(view(10L, GatheringType.PARTY), view(20L, GatheringType.PARTY)),
			)

			val grouped: GroupedGatherings = views.groupByType(GatheringType.entries)

			grouped.values.first { it.type == GatheringType.PARTY }.gatherings.map { it.id } shouldBe listOf(10L, 20L)
		}
	}
})
