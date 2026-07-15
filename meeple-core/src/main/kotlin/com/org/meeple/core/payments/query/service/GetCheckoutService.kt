package com.org.meeple.core.payments.query.service

import com.org.meeple.core.payments.query.dao.GetCheckoutOrdererDao
import com.org.meeple.core.payments.query.dao.GetPaymentMethodDao
import com.org.meeple.core.payments.query.dto.CheckoutView
import com.org.meeple.core.payments.query.dto.OrdererView
import com.org.meeple.core.payments.query.service.port.`in`.GetCheckoutUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 체크아웃 화면 조회 서비스. payments 도메인이 소유한 주문자 정보·활성 결제수단을 read model로 반환한다.
 * 주문자 정보 미비(본인인증 전 등)는 화면 진입을 막을 사유가 아니므로 null 필드로 내려주고 예외를 던지지 않는다.
 */
@Service
@Transactional(readOnly = true)
class GetCheckoutService(
	private val getCheckoutOrdererDao: GetCheckoutOrdererDao,
	private val getPaymentMethodDao: GetPaymentMethodDao,
) : GetCheckoutUseCase {

	override fun getCheckout(userId: Long): CheckoutView =
		CheckoutView(
			orderer = getCheckoutOrdererDao.findOrdererByUserId(userId) ?: OrdererView.empty(),
			paymentMethods = getPaymentMethodDao.findActiveMethods(),
		)
}
