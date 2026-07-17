package com.org.meeple.core.payments.command.application.port.`in`

import com.org.meeple.core.payments.command.application.port.`in`.command.CompletePaymentCommand
import com.org.meeple.core.payments.command.application.port.`in`.result.CompletePaymentResult

/**
 * 결제완료 접수 인포트(유스케이스). 좌석을 확보한 뒤 PG 승인을 거쳐, 성공 시 본인 성별을 확정해
 * 참가를 승인대기로 등록하고 결제 기록을 남긴다.
 */
interface CompletePaymentUseCase {

	fun complete(userId: Long, command: CompletePaymentCommand): CompletePaymentResult
}
