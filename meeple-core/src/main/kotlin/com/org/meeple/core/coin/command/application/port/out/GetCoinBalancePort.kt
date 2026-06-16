package com.org.meeple.core.coin.command.application.port.out

import com.org.meeple.core.coin.command.domain.CoinBalance

/** 코인 잔액 조회 아웃포트. */
interface GetCoinBalancePort {

	/** 사용자의 코인 잔액을 조회한다. 잔액 레코드가 없으면 null. */
	fun findByUserId(userId: Long): CoinBalance?

	/**
	 * 차감/적립 등 갱신을 위해 비관적 쓰기 락(SELECT ... FOR UPDATE)으로 잔액을 조회한다.
	 * 동일 트랜잭션이 커밋될 때까지 행을 잠가, 동시 차감으로 인한 잔액 음수화를 방지한다. 없으면 null.
	 */
	fun findByUserIdForUpdate(userId: Long): CoinBalance?
}
