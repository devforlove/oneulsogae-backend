package com.org.meeple.api.payments.response

import com.org.meeple.api.coin.response.CoinItemResponse
import com.org.meeple.core.coin.query.dto.CoinItem
import com.org.meeple.core.payments.query.dto.PaymentMethodViews

/** 코인 구매 체크아웃 화면 응답 — 구매할 코인 아이템 + 구매방법(활성 결제수단). */
data class CoinCheckoutResponse(
	val item: CoinItemResponse,
	val paymentMethods: List<PaymentMethodResponse>,
) {
	companion object {
		fun of(item: CoinItem, paymentMethods: PaymentMethodViews): CoinCheckoutResponse =
			CoinCheckoutResponse(
				item = CoinItemResponse.of(item),
				paymentMethods = PaymentMethodResponse.listOf(paymentMethods),
			)
	}
}
