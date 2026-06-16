package com.org.meeple.core.coin.command.application

import com.org.meeple.core.coin.CoinErrorCode
import com.org.meeple.core.coin.command.application.port.`in`.SpendCoinUseCase
import com.org.meeple.core.coin.command.application.port.`in`.command.SpendCoinCommand
import com.org.meeple.core.coin.command.application.port.out.GetCoinBalancePort
import com.org.meeple.core.coin.command.application.port.out.SaveCoinBalancePort
import com.org.meeple.core.coin.command.application.port.out.SaveCoinPort
import com.org.meeple.core.coin.command.domain.CoinHistory
import com.org.meeple.core.coin.command.domain.CoinBalance
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.time.TimeGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [SpendCoinUseCase] 구현.
 * 잔액 행을 비관적 락으로 잠근 뒤 충분한지 검사하고 차감한다. (동시 차감으로 인한 음수 잔액 방지)
 * 잔액 레코드가 없으면(적립 이력 없음) 잔액 부족으로 처리한다.
 * 차감 내역은 coins 원장에 음수 amount로 기록해 원장과 잔액의 정합성(SUM(coins) == balance)을 유지한다.
 */
@Service
class SpendCoinService(
	private val getCoinBalancePort: GetCoinBalancePort,
	private val saveCoinBalancePort: SaveCoinBalancePort,
	private val saveCoinPort: SaveCoinPort,
	private val timeGenerator: TimeGenerator,
) : SpendCoinUseCase {

	@Transactional
	override fun spend(userId: Long, command: SpendCoinCommand): CoinBalance {
		val balance: CoinBalance = getCoinBalancePort.findByUserIdForUpdate(userId)
			?: throw BusinessException(CoinErrorCode.INSUFFICIENT_COIN_BALANCE)

		if (!balance.canAfford(command.amount)) {
			throw BusinessException(CoinErrorCode.INSUFFICIENT_COIN_BALANCE)
		}

		// 차감 내역을 원장(coins)에 음수로 기록하고, 동일 트랜잭션에서 물질화 잔액을 감소시킨다.
		saveCoinPort.save(
			CoinHistory.spend(
				userId = userId,
				amount = command.amount,
				coinUsageType = command.coinUsageType,
				occurredAt = timeGenerator.now(),
			),
		)
		return saveCoinBalancePort.save(balance.subtract(command.amount))
	}
}
