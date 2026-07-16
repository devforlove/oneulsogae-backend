package com.org.meeple.core.payments.command.application.port.`in`

import com.org.meeple.core.payments.command.application.port.`in`.command.CompletePaymentCommand
import com.org.meeple.core.payments.command.application.port.`in`.result.CompletePaymentResult

/**
 * 결제완료 접수 인포트(유스케이스). 무검증 접수: 본인 성별을 확정해 참가를 승인대기로 등록하고 결제 기록을 남긴다.
 * 실제 결제수단 검증(PG)은 이후 과제다.
 */
interface CompletePaymentUseCase {

	fun complete(userId: Long, command: CompletePaymentCommand): CompletePaymentResult
}
