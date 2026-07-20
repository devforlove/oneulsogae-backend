package com.org.oneulsogae.core.payments.query.dto

/** 체크아웃 화면에 노출할 결제수단 한 건(read model). code는 프론트 계약 문자열(예: "BANK_TRANSFER")이다. */
data class PaymentMethodView(
	val code: String,
	val name: String,
)
