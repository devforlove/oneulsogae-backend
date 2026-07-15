package com.org.meeple.core.payments.query.dto

/**
 * 체크아웃(결제) 화면 진입 시 조회 데이터 read model — payments 도메인이 소유한 부분(주문자·결제수단).
 * 상품(모임 일정) 정보는 gathering 도메인 in-port로 컨트롤러가 별도 조합한다.
 */
data class CheckoutView(
	val orderer: OrdererView,
	val paymentMethods: PaymentMethodViews,
)
