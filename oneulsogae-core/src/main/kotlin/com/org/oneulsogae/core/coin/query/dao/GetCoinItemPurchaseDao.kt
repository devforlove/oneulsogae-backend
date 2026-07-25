package com.org.oneulsogae.core.coin.query.dao

/** 코인 패키지 구매 가드 조회 dao. 상점 필터·구매 선검사에 쓴다. */
interface GetCoinItemPurchaseDao {

	/** 사용자가 해당 상품을 이미 구매했는지 여부. */
	fun exists(userId: Long, itemId: Long): Boolean
}
