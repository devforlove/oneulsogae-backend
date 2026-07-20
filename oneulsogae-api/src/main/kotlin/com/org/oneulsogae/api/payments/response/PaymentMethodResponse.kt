package com.org.oneulsogae.api.payments.response

import com.org.oneulsogae.core.payments.query.dto.PaymentMethodView
import com.org.oneulsogae.core.payments.query.dto.PaymentMethodViews

/** 체크아웃 결제수단 응답 한 건. 활성 수단만 노출 순서대로 내려간다. */
data class PaymentMethodResponse(
	val code: String,
	val name: String,
) {
	companion object {
		fun listOf(views: PaymentMethodViews): List<PaymentMethodResponse> =
			views.values.map { view: PaymentMethodView ->
				PaymentMethodResponse(code = view.code, name = view.name)
			}
	}
}
