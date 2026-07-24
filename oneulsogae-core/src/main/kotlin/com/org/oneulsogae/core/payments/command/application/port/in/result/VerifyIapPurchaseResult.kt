package com.org.oneulsogae.core.payments.command.application.port.`in`.result

/** 인앱결제 검증·적립 결과 — 적립 후 총 코인 잔액. */
data class VerifyIapPurchaseResult(
	val coinBalance: Int,
)
