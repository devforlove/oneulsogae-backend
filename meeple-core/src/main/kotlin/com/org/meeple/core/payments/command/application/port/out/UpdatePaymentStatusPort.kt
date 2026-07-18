package com.org.meeple.core.payments.command.application.port.out

import com.org.meeple.core.payments.command.domain.PaymentStatus

/** 결제 기록의 상태를 전이하는 아웃포트. PG 승인 결과로 PENDING → APPROVED/FAILED 전이에 쓴다. */
interface UpdatePaymentStatusPort {

	/** [status]로 전이한다. FAILED 전이 시 [failReason]에 PG 거절 사유를 남겨 추적한다(성공 전이는 null). */
	fun updateStatus(paymentId: Long, status: PaymentStatus, failReason: String? = null)
}
