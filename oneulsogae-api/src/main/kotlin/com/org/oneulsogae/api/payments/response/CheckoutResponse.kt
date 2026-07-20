package com.org.oneulsogae.api.payments.response

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.gathering.query.dto.GatheringDetailView
import com.org.oneulsogae.core.gathering.query.dto.GatheringScheduleView
import com.org.oneulsogae.core.payments.query.dto.CheckoutView

/** 체크아웃(결제) 화면 진입 시 조회 데이터 응답 — 주문자·상품·결제수단. */
data class CheckoutResponse(
	val orderer: OrdererResponse,
	val product: ProductResponse,
	val paymentMethods: List<PaymentMethodResponse>,
) {
	companion object {
		fun of(
			userId: Long,
			view: CheckoutView,
			gathering: GatheringDetailView,
			schedule: GatheringScheduleView,
			gender: Gender,
		): CheckoutResponse =
			CheckoutResponse(
				orderer = OrdererResponse.of(userId, view.orderer),
				product = ProductResponse.of(gathering, schedule, gender),
				paymentMethods = PaymentMethodResponse.listOf(view.paymentMethods),
			)
	}
}
