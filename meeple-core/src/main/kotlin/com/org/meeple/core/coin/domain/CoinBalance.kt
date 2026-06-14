package com.org.meeple.core.coin.domain

/**
 * 사용자별 코인 잔액(총합)의 물질화(materialized) 도메인 모델.
 *
 * coins 적립 원장의 합을 캐시한 값으로, 적립 시 증가하고 차감 시 감소한다.
 * 원장(coins)이 진실의 원천이며, 이 잔액은 빠른 조회와 차감 시 동시성 제어를 위한 파생값이다.
 * 영속성은 [com.org.meeple.infra.coin.entity.CoinBalanceEntity]가 담당한다. (user당 1행)
 */
data class CoinBalance(
	val id: Long = 0,
	val userId: Long,
	val balance: Int,
) {

	/** 차감 가능 여부. (현재 잔액이 차감 수량 이상인지) */
	fun canAfford(amount: Int): Boolean {
		require(amount > 0) { "수량은 1 이상이어야 합니다." }
		return balance >= amount
	}

	/** 적립으로 잔액을 늘린 새 상태를 반환한다. */
	fun add(amount: Int): CoinBalance {
		require(amount > 0) { "적립 수량은 1 이상이어야 합니다." }
		return copy(balance = balance + amount)
	}

	/** 차감으로 잔액을 줄인 새 상태를 반환한다. 호출 전 [canAfford]로 잔액이 충분한지 확인해야 한다. */
	fun subtract(amount: Int): CoinBalance {
		require(amount > 0) { "차감 수량은 1 이상이어야 합니다." }
		require(balance >= amount) { "잔액이 부족합니다." }
		return copy(balance = balance - amount)
	}

	companion object {

		/** 잔액 레코드가 아직 없는 사용자의 0 잔액 초기 상태. */
		fun empty(userId: Long): CoinBalance =
			CoinBalance(userId = userId, balance = 0)
	}
}
