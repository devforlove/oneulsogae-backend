package com.org.oneulsogae.core.coin.command.application.port.`in`

import com.org.oneulsogae.core.coin.command.application.port.`in`.command.SpendCoinCommand
import com.org.oneulsogae.core.coin.command.domain.CoinBalance

/**
 * 사용자가 코인을 차감(사용)하는 인포트(유스케이스).
 * 차감 후 갱신된 잔액을 반환한다.
 */
interface SpendCoinUseCase {

	fun spend(userId: Long, command: SpendCoinCommand): CoinBalance
}
