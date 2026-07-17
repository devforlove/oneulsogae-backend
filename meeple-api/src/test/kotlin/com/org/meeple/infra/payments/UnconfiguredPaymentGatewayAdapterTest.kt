package com.org.meeple.infra.payments

import com.org.meeple.infra.payments.command.adapter.UnconfiguredPaymentGatewayAdapter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec

class UnconfiguredPaymentGatewayAdapterTest : DescribeSpec({

	describe("confirm") {
		it("호출 시 IllegalStateException을 던진다") {
			shouldThrow<IllegalStateException> {
				UnconfiguredPaymentGatewayAdapter().confirm(paymentKey = "key", orderId = "order", amount = 1000)
			}
		}
	}
})
