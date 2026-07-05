package com.org.meeple.core.gathering

import com.org.meeple.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

enum class GatheringErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	INVALID_TITLE("GATHER-001", "모임 제목은 필수입니다.", HttpStatus.BAD_REQUEST),
	TITLE_TOO_LONG("GATHER-002", "모임 제목은 100자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
	DESCRIPTION_TOO_LONG("GATHER-003", "모임 소개는 1000자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
	INVALID_CAPACITY("GATHER-004", "모임 정원은 최소 2명 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
	INVALID_GATHERING_AT("GATHER-005", "모임 일시는 현재 이후여야 합니다.", HttpStatus.BAD_REQUEST),
	INVALID_FEE("GATHER-006", "참가비는 0원 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
}
