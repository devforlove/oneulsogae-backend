package com.org.meeple.core.coin.domain

import kotlin.math.roundToInt

/**
 * 판매(구매) 가능한 코인 상품 도메인 모델.
 * 상품 구매 시 [coinAmount]만큼의 코인이 할인가([salePrice])에 지급되며, [price]는 정가다.
 * 영속성은 [com.org.meeple.infra.coin.entity.CoinItemEntity]가 담당한다.
 */
data class CoinItem(
	val id: Long = 0,
	val coinAmount: Int,
	val price: Int,
	val salePrice: Int,
) {

	/** 코인 1개당 실제 결제 가격. (salePrice / coinAmount) */
	val pricePerCoin: Double
		get() = if (coinAmount <= 0) 0.0 else salePrice.toDouble() / coinAmount

	/**
	 * 정가([price]) 대비 할인율(%)을 반환한다. (반올림한 정수 %)
	 * 정가가 0 이하이거나 할인가가 정가 이상이면 0을 반환한다.
	 */
	val discountRate: Int
		get() {
			if (price <= 0 || salePrice >= price) return 0
			val rate: Double = (price - salePrice).toDouble() / price * 100
			return rate.roundToInt()
		}

	companion object {

		/** 새 코인 상품을 생성한다. */
		fun create(coinAmount: Int, price: Int, salePrice: Int): CoinItem {
			require(coinAmount > 0) { "코인 개수는 1 이상이어야 합니다." }
			require(price > 0) { "정가는 1 이상이어야 합니다." }
			require(salePrice > 0) { "할인가는 1 이상이어야 합니다." }
			require(salePrice <= price) { "할인가는 정가보다 클 수 없습니다." }
			return CoinItem(coinAmount = coinAmount, price = price, salePrice = salePrice)
		}
	}
}
