package com.org.meeple.core.popup.domain

/**
 * 팝업([Popup]) 목록의 일급 컬렉션(first-class collection).
 * 원시 List를 그대로 노출하지 않고 감싸, 컬렉션에 대한 동작을 한곳에 응집시킨다.
 */
data class Popups(
	val values: List<Popup>,
) {

	/** 팝업 개수. */
	val size: Int
		get() = values.size

	/** 비어 있는지 여부. */
	fun isEmpty(): Boolean = values.isEmpty()

	companion object {

		/** 빈 팝업 목록. */
		fun empty(): Popups = Popups(emptyList())
	}
}
