package com.org.meeple.core.common.error

import com.org.meeple.core.common.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 전역 예외 핸들러.
 * 모든 에러 응답은 공통 봉투([ApiResponse])로 감싸 내려간다. (success=false, error에 코드/메시지)
 * - 커스텀 예외([BusinessException])는 지정한 에러 코드의 상태([ErrorCode.status])로 내려간다.
 * - 요청 값 검증 실패([MethodArgumentNotValidException])는 400(Bad Request)으로 내려간다.
 * - 요청 본문 해석 실패(잘못된 enum/타입 등)는 400(Bad Request)으로 내려간다.
 * - 그 외 모든 예외는 500(Internal Server Error)으로 내려간다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException::class)
	fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<Nothing>> =
		ResponseEntity
			.status(e.errorCode.status)
			.body(ApiResponse.error(ErrorResponse.of(e.errorCode, e.message)))

	@ExceptionHandler(MethodArgumentNotValidException::class)
	fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
		val message: String = e.bindingResult.fieldErrors
			.joinToString("; ") { fieldError: FieldError -> "${fieldError.field}: ${fieldError.defaultMessage}" }
			.ifBlank { "요청 값 검증에 실패했습니다." }
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(ApiResponse.error(ErrorResponse("INVALID_REQUEST", message)))
	}

	@ExceptionHandler(HttpMessageNotReadableException::class)
	fun handleNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Nothing>> =
		ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(ApiResponse.error(ErrorResponse("INVALID_REQUEST", "요청 본문을 해석할 수 없습니다.")))

	@ExceptionHandler(Exception::class)
	fun handleException(e: Exception): ResponseEntity<ApiResponse<Nothing>> =
		ResponseEntity
			.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ApiResponse.error(ErrorResponse("INTERNAL_ERROR", e.message ?: "서버 내부 오류가 발생했습니다.")))

}
