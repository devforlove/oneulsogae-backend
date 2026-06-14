package com.org.meeple.core.coin.application.port.out

import com.org.meeple.core.coin.domain.CoinItems

/** 코인 상품 조회 아웃포트. */
interface GetCoinItemPort {

	/** 등록된 전체 코인 상품을 조회한다. */
	fun findAll(): CoinItems
}
