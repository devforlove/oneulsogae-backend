package com.org.meeple.core.coin.query.dao

import com.org.meeple.core.coin.query.dto.CoinBalanceResult

/**
 * 코인 잔액 조회 dao. (조회 전용 read model 반환)
 * 갱신용 비관적 락 조회는 command 측 [com.org.meeple.core.coin.command.application.port.out.GetCoinBalancePort]가 담당한다.
 */
interface CoinBalanceQueryDao {

	/** 사용자의 현재 코인 잔액을 조회한다. 잔액 행이 없으면 null. */
	fun findByUserId(userId: Long): CoinBalanceResult?
}
