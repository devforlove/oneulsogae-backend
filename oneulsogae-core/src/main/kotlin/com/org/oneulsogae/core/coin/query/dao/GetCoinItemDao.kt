package com.org.oneulsogae.core.coin.query.dao

import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.coin.query.dto.CoinItems

/** 코인 상품 조회 dao. (코인 상점·체크아웃 조회 전용 read model 반환) */
interface GetCoinItemDao {

	/** 등록된 전체 코인 상품을 조회한다. */
	fun findAll(): CoinItems

	/** 코인 상품 한 건을 id로 조회한다. 없으면 null. */
	fun findById(itemId: Long): CoinItem?
}
