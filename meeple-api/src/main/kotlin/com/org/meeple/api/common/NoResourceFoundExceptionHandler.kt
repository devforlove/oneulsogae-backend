package com.org.meeple.api.common

import com.org.meeple.core.common.error.ErrorResponse
import com.org.meeple.core.common.response.ApiResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 매핑된 핸들러가 없는 경로 요청 처리. (정적 리소스 폴백에서 [NoResourceFoundException]이 던져진다)
 * core의 GlobalExceptionHandler catch-all(Exception→500)로 흘러가면 500 + error 로그가 남으므로 404로 내린다.
 * [NoResourceFoundException]은 서블릿(webmvc) 클래스라 core가 아닌 HTTP 경계(api)에 두고,
 * catch-all을 가진 어드바이스보다 먼저 매칭되도록 최우선 순위를 준다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class NoResourceFoundExceptionHandler {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	@ExceptionHandler(NoResourceFoundException::class)
	fun handleNoResourceFound(e: NoResourceFoundException): ResponseEntity<ApiResponse<Nothing>> {
		// 존재하지 않는 경로 호출(4xx)은 처리된 예외이므로 info로 남긴다.
		log.info("NoResourceFoundException: {}", e.resourcePath)
		return ResponseEntity
			.status(HttpStatus.NOT_FOUND)
			.body(ApiResponse.error(ErrorResponse("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.")))
	}
}
