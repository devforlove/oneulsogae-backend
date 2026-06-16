package com.org.meeple.core.coin.command.application.port.`in`

import com.org.meeple.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.meeple.core.coin.command.domain.CoinBalance

/**
 * 사용자가 코인을 구매/무료 획득하여 적립하는 인포트(유스케이스).
 * 적립 후 갱신된 잔액을 반환한다.
 */
interface AcquireCoinUseCase {

	fun acquire(userId: Long, command: AcquireCoinCommand): CoinBalance
}
