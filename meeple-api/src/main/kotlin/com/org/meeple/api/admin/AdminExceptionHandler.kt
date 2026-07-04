package com.org.meeple.api.admin

import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.core.common.error.ErrorResponse
import com.org.meeple.core.common.response.ApiResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 어드민 예외 핸들러. [AdminException]을 공통 응답 봉투([ApiResponse])로 변환한다.
 * (admin 모듈은 core에 의존하지 않으므로 core GlobalExceptionHandler가 잡지 못한다. 표현 계층인 api에서 매핑한다)
 * 응답 본문 구조는 core의 BusinessException 처리와 동일하게 유지한다. (success=false, error에 코드/메시지)
 */
@RestControllerAdvice
class AdminExceptionHandler {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	@ExceptionHandler(AdminException::class)
	fun handleAdminException(e: AdminException): ResponseEntity<ApiResponse<Nothing>> {
		log.info("AdminException: code={}, message={}", e.errorCode.code, e.message)
		return ResponseEntity
			.status(e.errorCode.status)
			.body(ApiResponse.error(ErrorResponse(e.errorCode.code, e.message)))
	}
}
