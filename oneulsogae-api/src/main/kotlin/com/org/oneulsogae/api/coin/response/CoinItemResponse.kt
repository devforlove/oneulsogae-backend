package com.org.oneulsogae.api.coin.response

import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.dto.CoinItems
import java.time.LocalDateTime

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
	/** 회원당 1회만 구매 가능한 패키지 여부. (클라이언트가 "한정" 뱃지 표시) */
	val oncePerUser: Boolean,
	/** 기간 한정 오퍼의 만료 시각(ISO-8601). 상시 상품이면 null. 클라이언트가 이 값으로 남은 시간을 계산한다. */
	val offerExpiresAt: LocalDateTime? = null,
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
				oncePerUser = coinItem.oncePerUser,
				offerExpiresAt = coinItem.offerExpiresAt,
			)

		/** 코인 상품 목록을 응답 목록으로 변환한다. */
		fun listOf(coinItems: CoinItems): List<CoinItemResponse> =
			coinItems.values.map { of(it) }
	}
}
