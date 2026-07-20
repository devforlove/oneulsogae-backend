package com.org.oneulsogae.admin.common.error

/**
 * 어드민 에러 응답 본문. 에러 코드와 메시지를 담는다.
 * (admin은 core에 의존하지 않으므로 core의 ErrorResponse를 재사용하지 않고 동일 필드로 자체 정의한다.
 *  JSON 계약(code/message)은 core와 일치시켜 클라이언트가 동일하게 파싱한다.)
 */
data class AdminErrorResponse(
	val code: String,
	val message: String,
)
