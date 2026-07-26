package com.org.oneulsogae.core.coin.query.dto

import java.time.LocalDateTime

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

	/** 기간 한정 상품(validDays != null)을 하나라도 포함하는지 여부. */
	fun hasTimeLimitedOffer(): Boolean = values.any { it.validDays != null }

	/** [now] 시점 기준 만료된 기간 한정 오퍼를 제거한 새 목록을 반환한다. (상시 상품은 유지) */
	fun activeOffersAt(userCreatedAt: LocalDateTime, now: LocalDateTime): CoinItems =
		CoinItems(values.filter { it.isOfferActiveAt(userCreatedAt, now) })

	companion object {

		/** 빈 코인 상품 목록. */
		fun empty(): CoinItems = CoinItems(emptyList())
	}
}
