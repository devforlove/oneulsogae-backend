package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.infra.payments.command.entity.PaymentMethodEntity

/** [PaymentMethodEntity] 테스트 픽스처. 기본은 활성 무통장입금이다. */
object PaymentMethodEntityFixture {

	fun create(
		code: String = "BANK_TRANSFER",
		name: String = "무통장입금",
		displayOrder: Int = 1,
		active: Boolean = true,
	): PaymentMethodEntity =
		PaymentMethodEntity(
			code = code,
			name = name,
			displayOrder = displayOrder,
			active = active,
		)
}
