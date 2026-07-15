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
}
