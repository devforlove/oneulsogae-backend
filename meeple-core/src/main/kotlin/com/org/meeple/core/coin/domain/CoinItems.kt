package com.org.meeple.core.coin.domain

/**
 * 코인 상품([CoinItem]) 목록의 일급 컬렉션(first-class collection).
 * 원시 List를 그대로 노출하지 않고 감싸, 컬렉션에 대한 동작을 한곳에 응집시킨다.
 */
data class CoinItems(
	val values: List<CoinItem>,
) {

	/** 상품 개수. */
	val size: Int
		get() = values.size

	/** 비어 있는지 여부. */
	fun isEmpty(): Boolean = values.isEmpty()

	companion object {

		/** 빈 코인 상품 목록. */
		fun empty(): CoinItems = CoinItems(emptyList())
	}
}
