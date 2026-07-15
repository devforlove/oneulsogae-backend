package com.org.meeple.core.payments.query.service.port.`in`

import com.org.meeple.core.payments.query.dto.CheckoutView

/** 결제(체크아웃) 화면 진입 시 필요한 데이터 조회 유스케이스(in-port). */
interface GetCheckoutUseCase {

	fun getCheckout(userId: Long): CheckoutView
}
