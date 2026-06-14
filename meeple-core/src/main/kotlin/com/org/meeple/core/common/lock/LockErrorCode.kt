package com.org.meeple.core.common.lock

import com.org.meeple.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 분산 락 관련 에러 코드.
 * [com.org.meeple.core.common.error.BusinessException]에 넘겨 사용한다.
 */
enum class LockErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	LOCK_ACQUISITION_FAILED("LOCK-001", "처리 중인 요청이 있습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.CONFLICT),
	LOCK_INTERRUPTED("LOCK-002", "락 획득이 중단되었습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
}
