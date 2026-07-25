package com.org.oneulsogae.core.payments.command.application.port.out

import com.org.oneulsogae.core.payments.command.domain.IapPayment

/** 인앱결제 기록 저장 out-port. transaction_id 유니크 위반 시 DataIntegrityViolationException. */
interface SaveIapPaymentPort {

	fun save(iapPayment: IapPayment): IapPayment
}
