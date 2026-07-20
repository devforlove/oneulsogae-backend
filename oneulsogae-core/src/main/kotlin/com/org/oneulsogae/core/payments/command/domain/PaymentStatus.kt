package com.org.oneulsogae.core.payments.command.domain

/**
 * 결제(PG 청구) 라이프사이클 상태. 참가 승인 상태(gathering_members.status)와는 다른 축이다.
 */
enum class PaymentStatus {
	/** 결제 기록 생성 — PG 최종 승인 이전. */
	PENDING,

	/** PG 최종 승인 성공. */
	APPROVED,

	/** PG 최종 승인 실패. */
	FAILED,
}
