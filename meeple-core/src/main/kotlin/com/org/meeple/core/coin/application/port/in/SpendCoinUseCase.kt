package com.org.meeple.core.coin.application.port.`in`

import com.org.meeple.core.coin.application.port.`in`.command.SpendCoinCommand
import com.org.meeple.core.coin.domain.CoinBalance

/**
 * 사용자가 코인을 차감(사용)하는 인포트(유스케이스).
 * 차감 후 갱신된 잔액을 반환한다.
 */
interface SpendCoinUseCase {

	fun spend(userId: Long, command: SpendCoinCommand): CoinBalance
}
