package com.org.oneulsogae.api.payments.response

import com.org.oneulsogae.api.coin.response.CoinItemResponse
import com.org.oneulsogae.core.coin.query.dto.CoinItem
import com.org.oneulsogae.core.payments.query.dto.PaymentMethodViews

/** 코인 구매 체크아웃 화면 응답 — 요청자 userId + 구매할 코인 아이템 + 구매방법(활성 결제수단). */
data class CoinCheckoutResponse(
	val userId: Long,
	val item: CoinItemResponse,
	val paymentMethods: List<PaymentMethodResponse>,
) {
	companion object {
		fun of(userId: Long, item: CoinItem, paymentMethods: PaymentMethodViews): CoinCheckoutResponse =
			CoinCheckoutResponse(
				userId = userId,
				item = CoinItemResponse.of(item),
				paymentMethods = PaymentMethodResponse.listOf(paymentMethods),
			)
	}
}
