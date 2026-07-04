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
import org.springframework.web.multipart.MaxUploadSizeExceededException

/**
 * 멀티파트 업로드 크기 한도(spring.servlet.multipart.max-file-size/max-request-size) 초과 처리.
 * 컨테이너가 요청 파싱 단계에서 [MaxUploadSizeExceededException]을 던지므로 컨트롤러의 도메인 검증에 닿지 못한다.
 * core의 catch-all(Exception→500)로 새지 않도록 최우선 어드바이스로 받아 413으로 내린다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class MultipartExceptionHandler {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	@ExceptionHandler(MaxUploadSizeExceededException::class)
	fun handleMaxUploadSize(e: MaxUploadSizeExceededException): ResponseEntity<ApiResponse<Nothing>> {
		// 클라이언트 입력 오류(4xx)이므로 info로 남긴다.
		log.info("MaxUploadSizeExceededException: {}", e.message)
		return ResponseEntity
			.status(HttpStatus.PAYLOAD_TOO_LARGE)
			.body(ApiResponse.error(ErrorResponse("USER-022", "파일이 너무 큽니다. 최대 10MB까지 업로드할 수 있습니다.")))
	}
}
