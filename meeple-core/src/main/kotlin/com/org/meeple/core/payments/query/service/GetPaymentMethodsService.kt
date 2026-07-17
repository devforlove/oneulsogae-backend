package com.org.meeple.core.payments.query.service

import com.org.meeple.core.payments.query.dao.GetPaymentMethodDao
import com.org.meeple.core.payments.query.dto.PaymentMethodViews
import com.org.meeple.core.payments.query.service.port.`in`.GetPaymentMethodsUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetPaymentMethodsUseCase] 구현. 활성 결제수단을 노출 순서대로 반환한다.
 * (payment_methods를 소유한 payments 도메인이 제공하는 재사용 조회 — 코인 체크아웃 등이 in-port로 쓴다)
 */
@Service
@Transactional(readOnly = true)
class GetPaymentMethodsService(
	private val getPaymentMethodDao: GetPaymentMethodDao,
) : GetPaymentMethodsUseCase {

	override fun getActiveMethods(): PaymentMethodViews =
		getPaymentMethodDao.findActiveMethods()
}
