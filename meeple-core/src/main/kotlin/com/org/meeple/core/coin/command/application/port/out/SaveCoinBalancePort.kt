package com.org.meeple.core.coin.command.application.port.out

import com.org.meeple.core.coin.command.domain.CoinBalance

/** 코인 잔액 저장 아웃포트. (신규 생성 또는 기존 잔액 갱신) */
interface SaveCoinBalancePort {

	fun save(coinBalance: CoinBalance): CoinBalance
}
