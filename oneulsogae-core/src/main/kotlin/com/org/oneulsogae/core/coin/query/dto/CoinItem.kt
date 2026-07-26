package com.org.oneulsogae.core.coin.query.dto

import com.org.oneulsogae.common.coin.CoinSaleChannel
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * 판매(구매) 가능한 코인 상품 도메인 모델.
 * 상품 구매 시 [coinAmount]만큼의 코인이 할인가([salePrice])에 지급되며, [price]는 정가다.
 * [oncePerUser]가 true면 회원당 한 번만 구매할 수 있는 패키지다.
 * [saleChannel]은 판매 채널(PG·IAP·BOTH)이며, IAP로 팔리는 상품(IAP·BOTH)은 스토어 SKU([storeProductId])가 필수다.
 * 영속성은 [com.org.oneulsogae.infra.coin.command.entity.CoinItemEntity]가 담당한다.
 */
data class CoinItem(
	val id: Long = 0,
	val coinAmount: Int,
	val price: Int,
	val salePrice: Int,
	val oncePerUser: Boolean = false,
	val saleChannel: CoinSaleChannel = CoinSaleChannel.PG,
	val storeProductId: String? = null,
	/** 유저 가입시각 기준 유효일수. null이면 상시 판매. N이면 가입시각 + N일까지만 노출·구매 가능. */
	val validDays: Int? = null,
	/** 이 유저 기준 오퍼 만료 시각(가입시각 + validDays). 조회 서비스가 채우는 파생값이며 DB 컬럼이 아니다. 상시 상품·미계산 시 null. */
	val offerExpiresAt: LocalDateTime? = null,
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

	/**
	 * 이 상품이 [now] 시점에 이 유저에게 판매 활성인지 여부.
	 * [validDays]가 null이면 상시(항상 true). N이면 가입시각([userCreatedAt]) + N일 직전까지 활성이다(만료 시각 exclusive).
	 */
	fun isOfferActiveAt(userCreatedAt: LocalDateTime, now: LocalDateTime): Boolean =
		validDays?.let { now.isBefore(userCreatedAt.plusDays(it.toLong())) } ?: true

	/** 이 유저 기준 오퍼 만료 시각을 계산한다. [validDays]가 null(상시)이면 null. */
	fun expiresAtFor(userCreatedAt: LocalDateTime): LocalDateTime? =
		validDays?.let { userCreatedAt.plusDays(it.toLong()) }

	companion object {

		/**
		 * 새 코인 상품을 생성한다.
		 * IAP로 팔리는 상품(IAP·BOTH)은 스토어 SKU([storeProductId])가 반드시 있어야 한다(공백 불가).
		 */
		fun create(
			coinAmount: Int,
			price: Int,
			salePrice: Int,
			oncePerUser: Boolean = false,
			saleChannel: CoinSaleChannel = CoinSaleChannel.PG,
			storeProductId: String? = null,
			validDays: Int? = null,
		): CoinItem {
			require(coinAmount > 0) { "코인 개수는 1 이상이어야 합니다." }
			require(price > 0) { "정가는 1 이상이어야 합니다." }
			require(salePrice > 0) { "할인가는 1 이상이어야 합니다." }
			require(salePrice <= price) { "할인가는 정가보다 클 수 없습니다." }
			if (saleChannel.sellableVia(CoinSaleChannel.IAP)) {
				require(!storeProductId.isNullOrBlank()) { "IAP 판매 상품은 스토어 상품 id가 필요합니다." }
			}
			return CoinItem(
				coinAmount = coinAmount,
				price = price,
				salePrice = salePrice,
				oncePerUser = oncePerUser,
				saleChannel = saleChannel,
				storeProductId = storeProductId,
				validDays = validDays,
			)
		}
	}
}
