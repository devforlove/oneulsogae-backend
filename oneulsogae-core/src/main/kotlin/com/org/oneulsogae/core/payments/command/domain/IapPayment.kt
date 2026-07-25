package com.org.oneulsogae.core.payments.command.domain

/**
 * 인앱결제(IAP) 코인 구매 기록. 스토어 영수증 검증·적립을 한 건으로 남긴다.
 * [transactionId]는 스토어 거래 식별자로 유니크다 — 같은 영수증 재검증을 [findByTransactionId]로 걸러 재적립을 막는다.
 * [status]는 스토어 결제 상태 축이며, 코인 지급 원장(coin_histories)과는 다른 축이다.
 */
class IapPayment(
	val id: Long? = null,
	val userId: Long,
	val itemId: Long,
	val platform: StorePlatform,
	val productId: String,
	val transactionId: String,
	val coinAmount: Int,
	val status: PaymentStatus,
)
