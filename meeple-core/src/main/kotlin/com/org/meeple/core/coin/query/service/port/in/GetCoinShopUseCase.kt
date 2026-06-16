package com.org.meeple.core.coin.query.service.port.`in`

import com.org.meeple.core.coin.query.dto.CoinItems

/** 코인 상점에 노출할 코인 상품 목록을 조회하는 인포트(유스케이스). */
interface GetCoinShopUseCase {

	fun getCoinShop(): CoinItems
}
