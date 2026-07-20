package com.org.oneulsogae.core.payments.command.application.port.out

/** PG 최종 승인(confirm) 아웃포트. 좌석 확보 후 서버 확정 금액으로 결제를 승인한다. */
interface PaymentGatewayPort {

	/** [paymentKey]·[orderId] 거래를 [amount]원으로 승인한다. 승인 여부와 실패 시 사유를 [PaymentConfirmResult]로 반환한다. */
	fun confirm(paymentKey: String, orderId: String, amount: Int): PaymentConfirmResult
}
