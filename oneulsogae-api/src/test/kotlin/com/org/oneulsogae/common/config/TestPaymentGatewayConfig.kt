package com.org.oneulsogae.common.config

import com.org.oneulsogae.core.payments.command.application.port.out.PaymentConfirmResult
import com.org.oneulsogae.core.payments.command.application.port.out.PaymentGatewayPort
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * 통합 테스트에서 PG 승인 아웃포트를 페이크로 대체한다. (실 Toss HTTP·시크릿키 미호출)
 * [FakePaymentGateway.result]로 다음 confirm 결과를 제어한다(기본: 승인). 실패 케이스는 테스트가 REJECTED로 세팅한다.
 * [AbstractIntegrationSupport]에 등록돼 모든 통합 테스트가 단일 컨텍스트를 공유한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestPaymentGatewayConfig {

	@Bean
	@Primary
	fun fakePaymentGatewayPort(): PaymentGatewayPort =
		object : PaymentGatewayPort {
			override fun confirm(paymentKey: String, orderId: String, amount: Int): PaymentConfirmResult =
				FakePaymentGateway.result
		}
}

object FakePaymentGateway {

	val APPROVED: PaymentConfirmResult = PaymentConfirmResult(approved = true, failReason = null)
	val REJECTED: PaymentConfirmResult = PaymentConfirmResult(approved = false, failReason = "FAKE_PG_REJECTED")

	/** 다음 confirm이 반환할 결과. 각 테스트가 제어하고, 테스트 사이 APPROVED로 되돌린다. */
	var result: PaymentConfirmResult = APPROVED
}
