package com.org.meeple.core.payments.command.application.port.out

import com.org.meeple.core.payments.command.domain.PaymentStatus

/** 코인 구매 결제 기록의 상태(PG 청구 라이프사이클) 전이 아웃포트. */
interface UpdateCoinPaymentStatusPort {

	fun updateStatus(coinPaymentId: Long, status: PaymentStatus, failReason: String? = null)
}
