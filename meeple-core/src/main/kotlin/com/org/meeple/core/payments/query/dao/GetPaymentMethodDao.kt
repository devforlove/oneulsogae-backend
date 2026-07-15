package com.org.meeple.core.payments.query.dao

import com.org.meeple.core.payments.query.dto.PaymentMethodViews

/**
 * 결제수단 조회 dao(out-port). infra의 GetPaymentMethodDaoImpl이 구현한다.
 * 활성(active) 수단만 노출 순서(displayOrder asc, id asc)로 투영한다.
 */
interface GetPaymentMethodDao {

	fun findActiveMethods(): PaymentMethodViews
}
