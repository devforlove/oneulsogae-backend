package com.org.oneulsogae.core.payments.command.domain

/**
 * 코인 구매 결제 기록(command 도메인 모델). 접수 시점에 PENDING으로 저장하고 PG 승인 결과로 APPROVED/FAILED로 전이한다.
 * 누가(userId)·어떤 코인 상품을(itemId)·몇 코인을(coinAmount)·얼마에(amount, 서버 확정가) 구매했는지 남긴다.
 * [paymentKey]는 PG 거래 식별자, [orderId]는 결제 요청 시 생성한 가맹점 주문번호(confirm·조회에 쓴다)다.
 * [status]는 PG 청구 라이프사이클이며, 실제 코인 지급 원장(coin_histories)과는 다른 축이다.
 */
class CoinPayment(
	val id: Long? = null,
	val userId: Long,
	val itemId: Long,
	val coinAmount: Int,
	val amount: Int,
	val paymentKey: String,
	val orderId: String,
	val status: PaymentStatus,
) {

	/** 이 기록의 주인이 [userId]인지. 같은 paymentKey 재접수의 재생 가부를 가른다. */
	fun isOwnedBy(userId: Long): Boolean = this.userId == userId

	/** PG 승인·코인 지급까지 끝난 기록인지. (APPROVED 전이는 적립 이후에만 일어난다) */
	fun isApproved(): Boolean = status == PaymentStatus.APPROVED

	/** PG 승인이 실패로 확정된 기록인지. 같은 인증키는 재승인되지 않는다. */
	fun isFailed(): Boolean = status == PaymentStatus.FAILED
}
