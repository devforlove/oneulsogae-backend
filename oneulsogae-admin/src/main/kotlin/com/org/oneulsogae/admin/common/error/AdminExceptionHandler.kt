package com.org.oneulsogae.admin.common.error

import com.org.oneulsogae.admin.common.response.AdminApiResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 어드민 예외 핸들러. [AdminException]을 어드민 공통 응답 봉투([AdminApiResponse])로 변환한다.
 * (admin 모듈은 core에 의존하지 않으므로 core GlobalExceptionHandler가 잡지 못하고 봉투도 공유하지 않는다.
 *  구동 앱(api)이 com.org.oneulsogae.** 를 컴포넌트 스캔하므로 admin 패키지의 이 @RestControllerAdvice가 등록된다.)
 * 응답 본문 구조는 core의 BusinessException 처리와 동일하게 유지한다. (success=false, error에 코드/메시지)
 *
 * core GlobalExceptionHandler에는 catch-all @ExceptionHandler(Exception)이 있어, advice 우선순위가 밀리면
 * AdminException이 그 폴백에 먼저 잡혀 500으로 샌다. 스캔 등록 순서에 의존하지 않도록 최우선 순위를 명시한다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class AdminExceptionHandler {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	@ExceptionHandler(AdminException::class)
	fun handleAdminException(e: AdminException): ResponseEntity<AdminApiResponse<Nothing>> {
		log.info("AdminException: code={}, message={}", e.errorCode.code, e.message)
		return ResponseEntity
			.status(e.errorCode.status)
			.body(AdminApiResponse.error(AdminErrorResponse(e.errorCode.code, e.message)))
	}
}
