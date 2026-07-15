package com.org.meeple.api.payments.response

import com.org.meeple.core.payments.query.dto.CheckoutView

/** 체크아웃(결제) 화면 진입 시 조회 데이터 응답. (추후 쿠폰 등 확장 지점) */
data class CheckoutResponse(
	val orderer: OrdererResponse,
) {
	companion object {
		fun of(view: CheckoutView): CheckoutResponse =
			CheckoutResponse(orderer = OrdererResponse.of(view.orderer))
	}
}
