package com.org.meeple.infra.payments

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.org.meeple.infra.payments.command.adapter.TossPaymentGatewayAdapter
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * [TossPaymentGatewayAdapter]의 confirm 유닛 테스트. WireMock으로 토스 승인 API를 스텁한다.
 * 요청 형태(경로·Basic 인증·JSON 본문)와 응답 상태→성공여부 매핑을 검증한다.
 */
class TossPaymentGatewayAdapterTest : DescribeSpec({

	val secretKey = "test_sk_example"
	val basicToken: String = Base64.getEncoder().encodeToString("$secretKey:".toByteArray(StandardCharsets.UTF_8))

	lateinit var server: WireMockServer
	lateinit var adapter: TossPaymentGatewayAdapter

	beforeSpec {
		server = WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
		server.start()
		val restClient: RestClient = RestClient.builder()
			.baseUrl(server.baseUrl())
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic $basicToken")
			.build()
		adapter = TossPaymentGatewayAdapter(restClient)
	}
	afterEach { server.resetAll() }
	afterSpec { server.stop() }

	describe("confirm") {
		it("토스가 2xx로 승인하면 true를 반환하고, Basic 인증·paymentKey·orderId·amount를 담아 요청한다") {
			server.stubFor(
				post(urlEqualTo("/v1/payments/confirm")).willReturn(
					aResponse().withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("""{"status":"DONE"}"""),
				),
			)

			adapter.confirm(paymentKey = "pk_1", orderId = "order_1", amount = 30000) shouldBe true

			server.verify(
				postRequestedFor(urlEqualTo("/v1/payments/confirm"))
					.withHeader(HttpHeaders.AUTHORIZATION, equalTo("Basic $basicToken"))
					.withRequestBody(equalToJson("""{"paymentKey":"pk_1","orderId":"order_1","amount":30000}""")),
			)
		}

		it("토스가 4xx로 거절하면 false를 반환한다") {
			server.stubFor(
				post(urlEqualTo("/v1/payments/confirm")).willReturn(
					aResponse().withStatus(400)
						.withHeader("Content-Type", "application/json")
						.withBody("""{"code":"REJECT_ACCOUNT_PAYMENT","message":"결제가 거절되었습니다."}"""),
				),
			)

			adapter.confirm(paymentKey = "pk_2", orderId = "order_2", amount = 30000) shouldBe false
		}
	}
})
