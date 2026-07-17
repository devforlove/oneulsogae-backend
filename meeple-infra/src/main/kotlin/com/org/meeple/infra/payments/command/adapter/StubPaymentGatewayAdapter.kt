package com.org.meeple.infra.payments.command.adapter

import com.org.meeple.core.payments.command.application.port.out.PaymentGatewayPort
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * [PaymentGatewayPort]의 스텁 구현. 실제 PG 없이 승인을 흉내낸다. (연동 전 단계용)
 * 요청 헤더 X-Stub-Pg-Confirm=fail이면 승인 실패, 없거나 그 외면 성공을 반환한다.
 * prod는 실제 PG 어댑터(@Profile("prod"), 미구현)가 담당한다.
 */
@Component
@Profile("!prod")
class StubPaymentGatewayAdapter : PaymentGatewayPort {

	override fun confirm(paymentKey: String, amount: Int): Boolean =
		confirmResult(stubHeader())

	private fun stubHeader(): String? =
		(RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request?.getHeader(STUB_HEADER)

	companion object {

		const val STUB_HEADER: String = "X-Stub-Pg-Confirm"

		/** 헤더 값이 "fail"이면 승인 실패(false), 그 외/없으면 성공(true). */
		fun confirmResult(header: String?): Boolean = header != "fail"
	}
}
