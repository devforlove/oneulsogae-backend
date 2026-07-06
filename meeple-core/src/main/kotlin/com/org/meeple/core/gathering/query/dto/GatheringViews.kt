package com.org.meeple.core.gathering.query.dto

import com.org.meeple.common.gathering.GatheringType

/**
 * 유저용 모임 목록([GatheringView])의 일급 컬렉션.
 * 원시 List를 그대로 노출하지 않고 감싸, 타입별 그룹핑 등 컬렉션 동작을 한곳에 응집시킨다.
 */
data class GatheringViews(
	val values: List<GatheringView>,
) {

	/**
	 * [types] 선언 순서대로 타입별 그룹을 만든다. 각 그룹은 이 목록에서 해당 타입 행만 추린 것으로,
	 * 원래 순서(최신 등록순)를 유지한다. 해당 타입 모임이 없으면 빈 리스트 그룹으로 포함한다.
	 */
	fun groupByType(types: List<GatheringType>): GroupedGatherings =
		GroupedGatherings(
			types.map { type: GatheringType ->
				GatheringTypeGroup(
					type = type,
					typeDescription = type.description,
					gatherings = values.filter { view: GatheringView -> view.type == type },
				)
			},
		)

	companion object {

		/** 빈 목록. */
		fun empty(): GatheringViews = GatheringViews(emptyList())
	}
}
