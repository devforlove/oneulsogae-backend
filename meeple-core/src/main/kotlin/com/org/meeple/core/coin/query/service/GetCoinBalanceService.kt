package com.org.meeple.core.coin.query.service

import com.org.meeple.core.coin.query.dao.CoinBalanceQueryDao
import com.org.meeple.core.coin.query.dto.CoinBalanceResult
import com.org.meeple.core.coin.query.service.port.`in`.GetCoinBalanceUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetCoinBalanceUseCase] 구현. (조회 전용 - 쓰기 부수효과를 두지 않는다)
 * 현재 코인 잔액을 조회한다. 잔액 행이 없으면(적립 이력이 없는 사용자) 0 잔액으로 응답한다.
 * 잔액 행 생성은 커맨드 경로([com.org.meeple.core.coin.command.application.CreateCoinBalanceService])가 담당한다.
 */
@Service
@Transactional(readOnly = true)
class GetCoinBalanceService(
	private val coinBalanceQueryDao: CoinBalanceQueryDao,
) : GetCoinBalanceUseCase {

	override fun getBalance(userId: Long): CoinBalanceResult =
		coinBalanceQueryDao.findByUserId(userId) ?: CoinBalanceResult(balance = 0)
}
