package com.org.oneulsogae.core.popup.query.dto

import com.org.oneulsogae.common.popup.PopupType

/**
 * 노출 팝업([PopupView]) 목록의 일급 컬렉션(first-class collection).
 * 원시 List를 그대로 노출하지 않고 감싸, 컬렉션에 대한 동작을 한곳에 응집시킨다.
 */
data class PopupViews(
	val values: List<PopupView>,
) {

	/** 팝업 개수. */
	val size: Int
		get() = values.size

	/** 비어 있는지 여부. */
	fun isEmpty(): Boolean = values.isEmpty()

	/** 일일 보상(DAILY_REWARD) 팝업이 목록에 포함되어 있는지 여부. (노출 시 출석 코인 적립 판단에 사용) */
	fun hasDailyReward(): Boolean =
		values.any { view: PopupView -> view.popUpType == PopupType.DAILY_REWARD }

	/** 일일 보상(DAILY_REWARD) 팝업을 제외한 새 목록을 반환한다. (이미 오늘 출석 코인을 받아 다시 노출할 필요가 없을 때) */
	fun withoutDailyReward(): PopupViews =
		PopupViews(values.filterNot { view: PopupView -> view.popUpType == PopupType.DAILY_REWARD })

	/** 신규 유저(NEW_USER) 팝업을 제외한 새 목록을 반환한다. (신규 유저가 아닌 요청에는 노출하지 않는다) */
	fun withoutNewUser(): PopupViews =
		PopupViews(values.filterNot { view: PopupView -> view.popUpType == PopupType.NEW_USER })

	/**
	 * 한 번 조회하면 제거할 팝업([PopupType.removeAfterView])의 id 목록. (환불 안내 등 1회성 개인 팝업)
	 * 조회 서비스가 이 id들을 명령 in-port에 넘겨 soft-delete해 다음 조회부터 노출되지 않게 한다.
	 */
	fun idsToRemoveAfterView(): List<Long> =
		values.filter { view: PopupView -> view.popUpType.removeAfterView }
			.map { view: PopupView -> view.id }

	/**
	 * 이 목록을 [lower]보다 앞에 두고 합친 새 목록을 만든다. (이 목록의 정렬 우선순위가 더 높다 — 예: 개인 팝업 > 전역 팝업)
	 * 각 그룹 내부는 display_order(동순위는 id) 오름차순으로 정렬하되, 그룹 간에는 재정렬하지 않아 이 목록이 항상 먼저 온다.
	 */
	fun mergeBefore(lower: PopupViews): PopupViews =
		PopupViews(sortedValues() + lower.sortedValues())

	/** values를 display_order(동순위는 id) 오름차순으로 정렬한다. */
	private fun sortedValues(): List<PopupView> =
		values.sortedWith(compareBy({ view: PopupView -> view.displayOrder }, { view: PopupView -> view.id }))

	companion object {

		/** 빈 팝업 목록. */
		fun empty(): PopupViews = PopupViews(emptyList())
	}
}
