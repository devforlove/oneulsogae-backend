package com.org.meeple.core.coin.query.service.port.`in`

import com.org.meeple.core.coin.query.dto.CoinBalanceResult

/** 사용자의 현재 코인 잔액을 조회하는 인포트(유스케이스). */
interface GetCoinBalanceUseCase {

	fun getBalance(userId: Long): CoinBalanceResult
}
