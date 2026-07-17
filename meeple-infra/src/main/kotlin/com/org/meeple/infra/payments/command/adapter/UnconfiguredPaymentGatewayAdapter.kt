package com.org.meeple.infra.payments.command.adapter

import com.org.meeple.core.payments.command.application.port.out.PaymentGatewayPort
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * [PaymentGatewayPort]의 prod 자리표시자(placeholder) 구현.
 * 실 PG(토스/포트원 등) 연동 전까지 prod 컨텍스트 기동을 위해서만 존재하며, 실호출 시 즉시 실패한다.
 * prod에서 결제가 조용히 승인되면 안 되므로(payments 미출시) 확실히 예외를 던진다.
 * 실 PG 어댑터 도입 시 이 클래스를 교체한다. [StubPaymentGatewayAdapter](@Profile("!prod"))와 프로파일 대칭.
 */
@Component
@Profile("prod")
class UnconfiguredPaymentGatewayAdapter : PaymentGatewayPort {

	override fun confirm(paymentKey: String, orderId: String, amount: Int): Boolean =
		throw IllegalStateException("PG 어댑터가 구성되지 않았습니다. 실제 PG 연동 전까지 prod에서 결제완료(confirm)를 사용할 수 없습니다.")
}
