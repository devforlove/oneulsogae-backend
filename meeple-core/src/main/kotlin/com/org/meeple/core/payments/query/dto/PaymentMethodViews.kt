package com.org.meeple.core.payments.query.dto

/** 결제수단 read model 일급 컬렉션. 노출 순서(displayOrder asc, id asc)가 유지된 목록을 담는다. */
data class PaymentMethodViews(
	val values: List<PaymentMethodView>,
)
