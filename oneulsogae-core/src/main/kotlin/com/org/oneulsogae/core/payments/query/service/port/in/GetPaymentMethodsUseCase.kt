package com.org.oneulsogae.core.payments.query.service.port.`in`

import com.org.oneulsogae.core.payments.query.dto.PaymentMethodViews

/** 활성 결제수단(구매방법) 목록을 조회하는 인포트(유스케이스). 다른 도메인(코인 등)의 체크아웃이 재사용한다. */
interface GetPaymentMethodsUseCase {

	fun getActiveMethods(): PaymentMethodViews
}
