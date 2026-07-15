package com.org.meeple.core.payments.query.dto

/**
 * 체크아웃(결제) 화면 진입 시 조회 데이터 read model.
 * 모임·일정·금액은 offline 도메인 API가 제공하므로 여기에 두지 않는다. (추후 쿠폰 등 확장 지점)
 */
data class CheckoutView(
	val orderer: OrdererView,
)
