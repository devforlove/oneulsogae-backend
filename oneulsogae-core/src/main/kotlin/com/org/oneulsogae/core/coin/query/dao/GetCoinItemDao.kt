package com.org.oneulsogae.core.coin.query.dao

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.dto.CoinItems

/** 코인 상품 조회 dao. (코인 상점·체크아웃·IAP 해석 read model 반환) */
interface GetCoinItemDao {

	/**
	 * 상점에 노출할 코인 상품을 조회한다.
	 * [channel] 또는 BOTH 채널 상품만 반환하며, once_per_user 상품 중 [userId]가 이미 구매한 것은 제외한다.
	 */
	fun findShopItems(userId: Long, channel: CoinSaleChannel): CoinItems

	/** 코인 상품 한 건을 id로 조회한다. 없으면 null. */
	fun findById(itemId: Long): CoinItem?

	/** 스토어 SKU로 코인 상품을 조회한다. IAP 검증의 SKU→coin_item 해석에 쓴다. 없으면 null. */
	fun findByStoreProductId(storeProductId: String): CoinItem?
}
