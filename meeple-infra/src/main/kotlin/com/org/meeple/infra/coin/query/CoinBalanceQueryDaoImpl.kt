package com.org.meeple.infra.coin.query

import com.org.meeple.core.coin.query.dao.CoinBalanceQueryDao
import com.org.meeple.core.coin.query.dto.CoinBalanceResult
import com.org.meeple.infra.coin.command.entity.CoinBalanceEntity
import com.org.meeple.infra.coin.command.repository.CoinBalanceJpaRepository
import org.springframework.stereotype.Component

/**
 * 코인 잔액 조회 dao([CoinBalanceQueryDao]) 구현.
 * 잔액 행을 읽어 [CoinBalanceResult] read model로 직접 투영한다. (command 도메인 CoinBalance에 의존하지 않는다)
 * entity·repository는 command 아래에 있고, infra 내부 query→command 참조는 허용된다.
 */
@Component
class CoinBalanceQueryDaoImpl(
	private val coinBalanceJpaRepository: CoinBalanceJpaRepository,
) : CoinBalanceQueryDao {

	override fun findByUserId(userId: Long): CoinBalanceResult? =
		coinBalanceJpaRepository.findByUserId(userId)?.let { entity: CoinBalanceEntity ->
			CoinBalanceResult(balance = entity.balance)
		}
}
