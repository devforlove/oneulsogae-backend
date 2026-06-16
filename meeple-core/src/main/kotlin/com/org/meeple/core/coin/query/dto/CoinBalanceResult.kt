package com.org.meeple.core.coin.query.dto

/**
 * 코인 잔액 조회 결과 읽기 모델.
 * 현재 잔액을 담는다.
 */
data class CoinBalanceResult(
	val balance: Int,
)
