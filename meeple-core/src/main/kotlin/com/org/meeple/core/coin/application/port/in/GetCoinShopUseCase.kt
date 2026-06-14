package com.org.meeple.core.coin.application.port.`in`

import com.org.meeple.core.coin.domain.CoinItems

/** 코인 상점에 노출할 코인 상품 목록을 조회하는 인포트(유스케이스). */
interface GetCoinShopUseCase {

	fun getCoinShop(): CoinItems
}
