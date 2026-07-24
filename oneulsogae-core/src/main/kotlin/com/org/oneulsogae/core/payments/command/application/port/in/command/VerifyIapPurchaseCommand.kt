package com.org.oneulsogae.core.payments.command.application.port.`in`.command

import com.org.oneulsogae.core.payments.command.domain.StorePlatform

/** 인앱결제 검증 커맨드 — 앱이 스토어 결제로 받은 영수증 정보. */
data class VerifyIapPurchaseCommand(
	val platform: StorePlatform,
	val productId: String,
	/** 통합 영수증 토큰 — iOS JWS / Android purchaseToken. */
	val purchaseToken: String,
	/** 스토어 거래 식별자 — 멱등 키. */
	val transactionId: String,
)
