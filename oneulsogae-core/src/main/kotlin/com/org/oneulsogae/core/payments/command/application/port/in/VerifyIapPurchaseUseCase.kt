package com.org.oneulsogae.core.payments.command.application.port.`in`

import com.org.oneulsogae.core.payments.command.application.port.`in`.command.VerifyIapPurchaseCommand
import com.org.oneulsogae.core.payments.command.application.port.`in`.result.VerifyIapPurchaseResult

/** 인앱결제 영수증을 검증하고 코인을 적립하는 유스케이스. */
interface VerifyIapPurchaseUseCase {

	fun verify(userId: Long, command: VerifyIapPurchaseCommand): VerifyIapPurchaseResult
}
