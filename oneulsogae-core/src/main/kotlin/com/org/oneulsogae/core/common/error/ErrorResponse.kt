package com.org.oneulsogae.core.common.error

/** 에러 응답 본문. 에러 코드와 메시지를 담는다. */
data class ErrorResponse(
	val code: String,
	val message: String,
) {
	companion object {
		fun of(errorCode: ErrorCode, message: String = errorCode.message): ErrorResponse =
			ErrorResponse(code = errorCode.code, message = message)
	}
}
