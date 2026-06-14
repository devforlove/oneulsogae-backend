package com.org.meeple.core.coin.application

import com.org.meeple.core.coin.application.port.`in`.CreateCoinBalanceUseCase
import com.org.meeple.core.coin.application.port.out.GetCoinBalancePort
import com.org.meeple.core.coin.application.port.out.SaveCoinBalancePort
import com.org.meeple.core.coin.domain.CoinBalance
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CreateCoinBalanceUseCase] 구현. (커맨드)
 * 잔액 행이 없는 사용자에게 0 잔액 행을 만들어, 이후 적립/차감([AcquireCoinService]/[SpendCoinService])이
 * 항상 기존 행을 갱신하도록 한다. 조회 경로([GetCoinBalanceService])는 쓰기를 하지 않는다.
 */
@Service
class CreateCoinBalanceService(
	private val getCoinBalancePort: GetCoinBalancePort,
	private val saveCoinBalancePort: SaveCoinBalancePort,
) : CreateCoinBalanceUseCase {

	@Transactional
	override fun createIfAbsent(userId: Long) {
		if (getCoinBalancePort.findByUserId(userId) == null) {
			saveCoinBalancePort.save(CoinBalance.empty(userId))
		}
	}
}
