package com.org.oneulsogae.core.payments.command.application.port.out

/** PG 최종 승인 결과. 실패면 [failReason]에 PG 응답 원문(거절 사유)을 담아 결제 기록의 실패 추적에 남긴다. */
data class PaymentConfirmResult(
	val approved: Boolean,
	val failReason: String?,
)
