package com.org.oneulsogae.api.coin.response

import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.dto.CoinItems

/** 코인 상점에 노출할 코인 상품 응답. */
data class CoinItemResponse(
	val id: Long,
	val coinAmount: Int,
	/** 정가. */
	val price: Int,
	/** 할인가. (실제 결제 가격) */
	val salePrice: Int,
	/** 코인 1개당 가격. (할인가 기준, 소수점 제외 자연수 부분만) */
	val pricePerCoin: Int,
	/** 정가 대비 할인율(%). */
	val discountRate: Int,
) {
	companion object {
		fun of(coinItem: CoinItem): CoinItemResponse =
			CoinItemResponse(
				id = coinItem.id,
				coinAmount = coinItem.coinAmount,
				price = coinItem.price,
				salePrice = coinItem.salePrice,
				pricePerCoin = coinItem.pricePerCoin.toInt(),
				discountRate = coinItem.discountRate,
			)

		/** 코인 상품 목록을 응답 목록으로 변환한다. */
		fun listOf(coinItems: CoinItems): List<CoinItemResponse> =
			coinItems.values.map { of(it) }
	}
}
