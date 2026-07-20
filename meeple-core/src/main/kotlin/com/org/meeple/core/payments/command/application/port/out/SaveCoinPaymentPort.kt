package com.org.meeple.core.payments.command.application.port.out

import com.org.meeple.core.payments.command.domain.CoinPayment

/** 코인 구매 결제 기록 저장 아웃포트. */
interface SaveCoinPaymentPort {

	fun save(coinPayment: CoinPayment): CoinPayment
}
