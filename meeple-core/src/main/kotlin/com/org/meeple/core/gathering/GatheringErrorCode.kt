package com.org.meeple.core.gathering

import com.org.meeple.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 유저용 모임(gathering) 도메인 에러 코드.
 * [com.org.meeple.core.common.error.BusinessException]에 넘겨 사용한다.
 * (어드민 모듈의 `AdminErrorCode`(GATHER-*)와는 별개 네임스페이스다)
 */
enum class GatheringErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	/** 모집중 모임을 id로 찾지 못함(없거나 모집중이 아님). */
	GATHERING_NOT_FOUND("GATHERING-001", "모임을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
}
