package com.org.meeple.api.coin.response

import com.org.meeple.core.coin.query.dto.CoinBalanceResult
import com.org.meeple.core.coin.command.domain.CoinBalance

/** 코인 잔액 응답. 잔액만 내려준다. */
data class CoinBalanceResponse(
	val balance: Int,
) {
	companion object {
		/** 적립/차감 응답. */
		fun of(coinBalance: CoinBalance): CoinBalanceResponse =
			CoinBalanceResponse(balance = coinBalance.balance)

		/** 잔액 조회 응답. (마지막 DAILY 수령일 등 부가 정보는 내려주지 않는다) */
		fun of(result: CoinBalanceResult): CoinBalanceResponse =
			CoinBalanceResponse(balance = result.balance)
	}
}
