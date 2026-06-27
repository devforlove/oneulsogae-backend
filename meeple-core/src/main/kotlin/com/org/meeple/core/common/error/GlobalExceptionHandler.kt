package com.org.meeple.core.common.error

import com.org.meeple.core.common.response.ApiResponse
import jakarta.validation.ConstraintViolationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException

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

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	@ExceptionHandler(BusinessException::class)
	fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
		// 처리된(예상된) 비즈니스 예외는 info로 남긴다. (스택 트레이스 불필요)
		log.info("BusinessException: code={}, message={}", e.errorCode.code, e.message)
		return ResponseEntity
			.status(e.errorCode.status)
			.body(ApiResponse.error(ErrorResponse.of(e.errorCode, e.message)))
	}

	@ExceptionHandler(MethodArgumentNotValidException::class)
	fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
		val message: String = e.bindingResult.fieldErrors
			.joinToString("; ") { fieldError: FieldError -> "${fieldError.field}: ${fieldError.defaultMessage}" }
			.ifBlank { "요청 값 검증에 실패했습니다." }
		// 클라이언트 입력 오류(4xx)는 처리된 예외이므로 info로 남긴다.
		log.info("MethodArgumentNotValidException: {}", message)
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(ApiResponse.error(ErrorResponse("INVALID_REQUEST", message)))
	}

	/**
	 * Spring 7.x 메서드 수준 검증 실패 처리. (@Validated 컨트롤러 + @Valid/@NotBlank 파라미터)
	 * [MethodArgumentNotValidException]이 [HandlerMethodValidationException]의 하위 타입이므로
	 * 이 핸들러는 @ModelAttribute/@RequestParam 검증 실패를 400으로 내린다.
	 * (단, [MethodArgumentNotValidException]이 먼저 매칭되어 우선 처리된다)
	 */
	@ExceptionHandler(HandlerMethodValidationException::class)
	fun handleMethodValidation(e: HandlerMethodValidationException): ResponseEntity<ApiResponse<Nothing>> {
		val message: String = e.allErrors
			.joinToString("; ") { error: org.springframework.context.MessageSourceResolvable ->
				error.defaultMessage ?: "검증 실패"
			}
			.ifBlank { "요청 값 검증에 실패했습니다." }
		log.info("HandlerMethodValidationException: {}", message)
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(ApiResponse.error(ErrorResponse("INVALID_REQUEST", message)))
	}

	/**
	 * Jakarta Bean Validation 제약 위반 처리. (수동 validator.validate() 호출 결과)
	 * 컨트롤러에서 명시적으로 검증을 호출하고 [ConstraintViolationException]을 던지는 경우 400으로 내린다.
	 */
	@ExceptionHandler(ConstraintViolationException::class)
	fun handleConstraintViolation(e: ConstraintViolationException): ResponseEntity<ApiResponse<Nothing>> {
		val message: String = e.constraintViolations
			.joinToString("; ") { it.message }
			.ifBlank { "요청 값 검증에 실패했습니다." }
		log.info("ConstraintViolationException: {}", message)
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(ApiResponse.error(ErrorResponse("INVALID_REQUEST", message)))
	}

	/**
	 * 낙관적 락 충돌 처리. 같은 애그리거트를 동시에 수정해 버전이 어긋나면(예: 팀 탈퇴 ↔ 관심/수락 경합)
	 * 트랜잭션이 롤백되며 이 예외가 오른다. 분산 락 충돌([LockErrorCode.LOCK_ACQUISITION_FAILED])과 같은 의미라 409로 내린다.
	 */
	@ExceptionHandler(OptimisticLockingFailureException::class)
	fun handleOptimisticLock(e: OptimisticLockingFailureException): ResponseEntity<ApiResponse<Nothing>> {
		// 처리된(예상된) 동시성 충돌이므로 info로 남긴다. (스택 트레이스 불필요)
		log.info("OptimisticLockingFailureException: {}", e.message)
		return ResponseEntity
			.status(HttpStatus.CONFLICT)
			.body(ApiResponse.error(ErrorResponse("CONFLICT", "처리 중인 요청이 있습니다. 잠시 후 다시 시도해 주세요.")))
	}

	@ExceptionHandler(HttpMessageNotReadableException::class)
	fun handleNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Nothing>> {
		// 클라이언트 입력 오류(4xx)는 처리된 예외이므로 info로 남긴다.
		log.info("HttpMessageNotReadableException: {}", e.message)
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(ApiResponse.error(ErrorResponse("INVALID_REQUEST", "요청 본문을 해석할 수 없습니다.")))
	}

	@ExceptionHandler(Exception::class)
	fun handleException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
		// 예상치 못한 예외(5xx)는 스택 트레이스와 함께 error로 남긴다.
		log.error("Unhandled exception", e)
		return ResponseEntity
			.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ApiResponse.error(ErrorResponse("INTERNAL_ERROR", e.message ?: "서버 내부 오류가 발생했습니다.")))
	}

}
