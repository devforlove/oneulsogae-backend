package com.org.meeple.core.inquiry

import com.org.meeple.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

enum class InquiryErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	INVALID_EMAIL("INQ-001", "유효한 이메일 형식이 아닙니다.", HttpStatus.BAD_REQUEST),
	MESSAGE_TOO_SHORT("INQ-002", "문의 내용은 최소 10자 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
	MESSAGE_TOO_LONG("INQ-003", "문의 내용은 1000자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
}
