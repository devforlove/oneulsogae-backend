package com.org.meeple.core.payments

import com.org.meeple.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 결제(payments) 도메인 에러 코드.
 * [com.org.meeple.core.common.error.BusinessException]에 넘겨 사용한다.
 */
enum class PaymentsErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	/** 체크아웃 대상 일정을 모임에서 찾지 못함(scheduleId 미매칭). */
	CHECKOUT_PRODUCT_NOT_FOUND("PAYMENTS-001", "결제할 일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 결제 접수에 필요한 주문자 성별이 프로필에 없음(온보딩 미완료 등). */
	ORDERER_GENDER_REQUIRED("PAYMENTS-002", "주문자 성별을 확인할 수 없습니다. 프로필을 먼저 완성해주세요.", HttpStatus.BAD_REQUEST),

	/** 결제완료 접수의 productId가 본인 프로필 성별의 상품이 아님. */
	PAYMENT_PRODUCT_GENDER_MISMATCH("PAYMENTS-003", "본인 성별의 상품이 아닙니다.", HttpStatus.BAD_REQUEST),

	/** PG 최종 승인(confirm) 실패. 좌석은 복원되고 청구되지 않는다. */
	PAYMENT_CONFIRM_FAILED("PAYMENTS-004", "결제 승인에 실패했습니다. 다시 시도해주세요.", HttpStatus.PAYMENT_REQUIRED),
}
