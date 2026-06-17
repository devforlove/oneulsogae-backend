package com.org.meeple.core.coin.query.dao

import com.org.meeple.core.coin.query.dto.CoinItems

/** 코인 상품 조회 dao. (코인 상점 조회 전용 read model 반환) */
interface GetCoinItemDao {

	/** 등록된 전체 코인 상품을 조회한다. */
	fun findAll(): CoinItems
}
