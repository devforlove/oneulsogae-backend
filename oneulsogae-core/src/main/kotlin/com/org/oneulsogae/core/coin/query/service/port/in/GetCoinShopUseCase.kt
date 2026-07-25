package com.org.oneulsogae.core.coin.query.service.port.`in`

import com.org.oneulsogae.common.coin.CoinSaleChannel
import com.org.oneulsogae.core.coin.query.dto.CoinItems

/** 코인 상점에 노출할 코인 상품 목록을 조회하는 인포트(유스케이스). */
interface GetCoinShopUseCase {

	/** [channel]·BOTH 상품 중 [userId]가 아직 못 산 것(1회 패키지 구매분 제외)을 반환한다. */
	fun getCoinShop(userId: Long, channel: CoinSaleChannel): CoinItems
}
