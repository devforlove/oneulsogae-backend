package com.org.meeple.core.solomatch

import com.org.meeple.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 매칭 도메인 에러 코드.
 * [com.org.meeple.core.common.error.BusinessException]에 넘겨 사용한다.
 */
enum class MatchErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	MATCH_NOT_FOUND("MATCH-001", "매칭을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	NOT_MATCH_PARTICIPANT("MATCH-002", "해당 매칭의 참가자가 아닙니다.", HttpStatus.FORBIDDEN),
	MATCH_ALREADY_CLOSED("MATCH-003", "이미 종료된 매칭입니다.", HttpStatus.CONFLICT),
	MATCH_NOT_MATCHED("MATCH-009", "성사된 매칭만 종료할 수 있습니다.", HttpStatus.CONFLICT),
	MATCH_BATCH_ALREADY_RUNNING("MATCH-008", "매칭 배치가 이미 실행 중입니다.", HttpStatus.CONFLICT),
}
