package com.org.oneulsogae.core.coin.query.service.port.`in`

import com.org.oneulsogae.core.coin.query.dto.CoinItem

/** 스토어 SKU로 코인 상품을 조회하는 인포트. IAP 검증이 SKU→coin_item 해석에 쓴다. */
interface GetCoinItemBySkuUseCase {

	/** [storeProductId](SKU)에 해당하는 코인 상품. 없으면 COIN_ITEM_NOT_FOUND. */
	fun getBySku(storeProductId: String): CoinItem
}
