package com.org.oneulsogae.core.coin.query.service.port.`in`

import com.org.oneulsogae.core.coin.query.dto.CoinItem

/** 코인 구매 체크아웃 화면에 노출할 코인 아이템을 조회하는 인포트(유스케이스). */
interface GetCoinCheckoutUseCase {

	fun getCheckout(itemId: Long): CoinItem
}
