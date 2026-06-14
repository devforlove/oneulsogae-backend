package com.org.meeple.core.coin.application

import com.org.meeple.core.coin.application.port.`in`.GetCoinBalanceUseCase
import com.org.meeple.core.coin.application.port.`in`.result.CoinBalanceResult
import com.org.meeple.core.coin.application.port.out.GetCoinBalancePort
import com.org.meeple.core.coin.domain.CoinBalance
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetCoinBalanceUseCase] 구현. (조회 전용 - 쓰기 부수효과를 두지 않는다)
 * 현재 코인 잔액을 조회한다. 잔액 행이 없으면(적립 이력이 없는 사용자) 0 잔액으로 응답한다.
 * 잔액 행 생성은 커맨드 경로([com.org.meeple.core.coin.application.CreateCoinBalanceService])가 담당한다.
 */
@Service
@Transactional(readOnly = true)
class GetCoinBalanceService(
	private val getCoinBalancePort: GetCoinBalancePort,
) : GetCoinBalanceUseCase {

	override fun getBalance(userId: Long): CoinBalanceResult {
		val coinBalance: CoinBalance = getCoinBalancePort.findByUserId(userId) ?: CoinBalance.empty(userId)
		return CoinBalanceResult(balance = coinBalance.balance)
	}
}
