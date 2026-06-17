package com.org.meeple.infra.coin.query

import com.org.meeple.core.coin.query.dao.CoinBalanceDao
import com.org.meeple.core.coin.query.dto.CoinBalanceResult
import com.org.meeple.infra.coin.command.entity.QCoinBalanceEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * 코인 잔액 조회 dao([CoinBalanceDao])의 QueryDSL 구현.
 * 잔액 행을 읽어 [CoinBalanceResult] read model로 직접 투영한다. (command 도메인 CoinBalance에 의존하지 않는다)
 */
@Component
class CoinBalanceDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : CoinBalanceDao {

	override fun findByUserId(userId: Long): CoinBalanceResult? {
		val balance: QCoinBalanceEntity = QCoinBalanceEntity.coinBalanceEntity
		return queryFactory
			.select(Projections.constructor(CoinBalanceResult::class.java, balance.balance))
			.from(balance)
			.where(balance.userId.eq(userId))
			.fetchOne()
	}
}
