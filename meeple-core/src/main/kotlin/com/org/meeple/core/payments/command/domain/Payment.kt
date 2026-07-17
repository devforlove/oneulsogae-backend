package com.org.meeple.core.payments.command.domain

import com.org.meeple.common.user.Gender

/**
 * 결제 기록(command 도메인 모델). 접수 시점에 PENDING으로 저장하고 PG 승인 결과로 APPROVED/FAILED로 전이한다.
 * 결제수단 등 상세는 없고 누가(userId)·무엇을(gathering/schedule/product/gender)·얼마에(amount, 서버 확정가) 접수했는지만 남긴다.
 * [productId]는 결제완료 요청의 상품 id(가격 근거) — 좌석 차감 추적(earlyBirdApplied)은 gathering_members가 가진다.
 * [paymentKey]는 PG 거래 식별자다.
 * [status]는 PG 청구 라이프사이클이며, 참가 승인 원장(gathering_members.status)과는 다른 축이다.
 */
class Payment(
	val id: Long? = null,
	val userId: Long,
	val gatheringId: Long,
	val scheduleId: Long,
	val productId: Long,
	val gender: Gender,
	val amount: Int,
	val paymentKey: String,
	val status: PaymentStatus,
)
