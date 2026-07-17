package com.org.meeple.infra.payments

import com.org.meeple.infra.payments.command.adapter.StubPaymentGatewayAdapter
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class StubPaymentGatewayAdapterTest : DescribeSpec({

	describe("confirmResult") {
		it("헤더가 fail이면 승인 실패(false)") {
			StubPaymentGatewayAdapter.confirmResult("fail") shouldBe false
		}
		it("헤더가 없으면 승인 성공(true)") {
			StubPaymentGatewayAdapter.confirmResult(null) shouldBe true
		}
		it("헤더가 fail이 아니면 승인 성공(true)") {
			StubPaymentGatewayAdapter.confirmResult("anything") shouldBe true
		}
	}
})
