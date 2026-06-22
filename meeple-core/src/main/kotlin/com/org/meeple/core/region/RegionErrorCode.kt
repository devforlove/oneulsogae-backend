package com.org.meeple.core.region

import com.org.meeple.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 지역(region) 도메인 에러 코드.
 * [com.org.meeple.core.common.error.BusinessException]에 넘겨 사용한다.
 */
enum class RegionErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	REGION_NOT_FOUND("REGION-001", "활동지역을 찾을 수 없습니다.", HttpStatus.BAD_REQUEST),
}
