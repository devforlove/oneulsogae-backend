package com.org.oneulsogae.infra.payments.command.adapter

import com.org.oneulsogae.core.payments.command.application.port.out.PaymentConfirmResult
import com.org.oneulsogae.core.payments.command.application.port.out.PaymentGatewayPort
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

/**
 * 토스페이먼츠 결제 승인 어댑터. (POST {baseUrl}/v1/payments/confirm)
 * SDK 결제창 인증 후 받은 paymentKey·orderId와 서버 확정 amount로 최종 승인한다.
 * 2xx(status DONE)면 승인 성공, 토스가 4xx/5xx로 거절하면 승인 실패(응답 원문을 사유로 남긴다).
 * 통신 자체가 실패(타임아웃 등)하면 예외를 그대로 전파한다 — 승인 여부가 불확실한데 실패로 단정하면
 * 실제로는 청구됐을 결제를 FAILED 처리하고 좌석까지 복원하게 되므로, PENDING을 durable하게 남겨 재점검하게 둔다.
 * ⚠️ 테스트 시크릿키 연동 단계다. 라이브 전환 시 TOSS_SECRET_KEY 환경변수로 실키를 주입한다.
 * 모든 프로파일에서 활성이라 로컬 개발도 실제 Toss 샌드박스를 호출한다. 통합 테스트는 @Primary 페이크 포트로 대체한다.
 */
@Component
class TossPaymentGatewayAdapter(
	private val tossRestClient: RestClient,
) : PaymentGatewayPort {

	override fun confirm(paymentKey: String, orderId: String, amount: Int): PaymentConfirmResult =
		try {
			tossRestClient.post()
				.uri("/v1/payments/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.body(mapOf("paymentKey" to paymentKey, "orderId" to orderId, "amount" to amount))
				.retrieve()
				.toBodilessEntity()
			PaymentConfirmResult(approved = true, failReason = null)
		} catch (e: RestClientResponseException) {
			// 토스가 명시적으로 거절(금액 불일치·이미 처리됨·인증 만료 등). 응답 원문을 실패 사유로 남긴다. 서비스가 좌석 복원 후 402.
			log.warn("토스 결제 승인 거절: orderId={}, status={}, body={}", orderId, e.statusCode, e.responseBodyAsString)
			PaymentConfirmResult(approved = false, failReason = e.responseBodyAsString)
		}

	companion object {
		private val log: Logger = LoggerFactory.getLogger(TossPaymentGatewayAdapter::class.java)
	}
}
