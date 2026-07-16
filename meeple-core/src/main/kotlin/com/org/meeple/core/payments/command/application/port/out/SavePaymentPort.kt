package com.org.meeple.core.payments.command.application.port.out

import com.org.meeple.core.payments.command.domain.Payment

/** 결제 기록을 저장하는 아웃포트. */
interface SavePaymentPort {

	fun save(payment: Payment): Payment
}
