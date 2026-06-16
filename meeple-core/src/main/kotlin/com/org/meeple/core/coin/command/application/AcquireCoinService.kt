package com.org.meeple.core.coin.command.application

import com.org.meeple.common.coin.CoinGetType
import com.org.meeple.core.coin.CoinErrorCode
import com.org.meeple.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.meeple.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.meeple.core.coin.command.application.port.out.GetCoinBalancePort
import com.org.meeple.core.coin.command.application.port.out.GetCoinPort
import com.org.meeple.core.coin.command.application.port.out.SaveCoinBalancePort
import com.org.meeple.core.coin.command.application.port.out.SaveCoinPort
import com.org.meeple.core.coin.command.domain.CoinHistory
import com.org.meeple.core.coin.command.domain.CoinBalance
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.time.TimeGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [AcquireCoinUseCase] 구현.
 * 획득/구매한 코인을 적립 내역(coins)으로 저장하고, 동일 트랜잭션에서 물질화 잔액(coin_balances)을 함께 증가시킨 뒤
 * 갱신된 잔액을 반환한다.
 */
@Service
class AcquireCoinService(
	private val saveCoinPort: SaveCoinPort,
	private val getCoinPort: GetCoinPort,
	private val getCoinBalancePort: GetCoinBalancePort,
	private val saveCoinBalancePort: SaveCoinBalancePort,
	private val timeGenerator: TimeGenerator,
) : AcquireCoinUseCase {

	@Transactional
	override fun acquire(userId: Long, command: AcquireCoinCommand): CoinBalance {
		// 차감 전용 타입(SPEND)은 적립 경로로 들어올 수 없다. (차감은 SpendCoinUseCase로만)
		if (command.coinType.isSpending) {
			throw BusinessException(CoinErrorCode.INVALID_ACQUIRE_COIN_TYPE)
		}

		val now: LocalDateTime = timeGenerator.now()

		// DAILY 코인은 하루에 한 번만 적립할 수 있다.
		if (command.coinType == CoinGetType.DAILY) {
			verifyDailyCoinNotAcquiredToday(userId, now)
		}

		// 적립 내역을 coins 원장에 기록한다.
		saveCoinPort.save(
			CoinHistory.acquire(
				userId = userId,
				amount = command.amount,
				coinType = command.coinType,
				acquiredAt = now,
			),
		)

		// 적립 내역과 같은 트랜잭션에서 잔액을 증가시켜 원장과 잔액의 정합성을 유지하고, 갱신된 잔액을 반환한다.
		// 잔액 행이 없으면(첫 적립) 0에서 시작한다. 갱신을 위해 비관적 락으로 잠근다.
		val balance: CoinBalance = getCoinBalancePort.findByUserIdForUpdate(userId)
			?: CoinBalance.empty(userId)
		return saveCoinBalancePort.save(balance.add(command.amount))
	}

	/**
	 * 사용자가 오늘(0시~내일 0시) 이미 DAILY 코인을 적립했는지 검사하고, 이미 받았으면 중복 적립을 막는다.
	 */
	private fun verifyDailyCoinNotAcquiredToday(userId: Long, now: LocalDateTime) {
		val startOfToday: LocalDateTime = now.toLocalDate().atStartOfDay()
		if (getCoinPort.existsAcquired(userId, CoinGetType.DAILY, startOfToday, startOfToday.plusDays(1))) {
			throw BusinessException(CoinErrorCode.DAILY_COIN_ALREADY_ACQUIRED)
		}
	}
}
