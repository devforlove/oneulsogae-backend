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

	/** 참가 신청 대상 일정을 찾지 못함(없거나 요청 모임 소속이 아님). */
	GATHERING_SCHEDULE_NOT_FOUND("GATHERING-002", "모임 일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 판매 중(예정 상태)이 아닌 일정에 참가 신청함. */
	GATHERING_SCHEDULE_NOT_OPEN("GATHERING-003", "참가 신청할 수 없는 일정입니다.", HttpStatus.CONFLICT),

	/** 해당 성별 정원이 모두 찼음(승인대기 포함). */
	GATHERING_SOLD_OUT("GATHERING-004", "정원이 마감되었습니다.", HttpStatus.CONFLICT),

	/** 같은 일정에 이미 승인대기 또는 참가 상태의 신청이 있음. */
	GATHERING_ALREADY_JOINED("GATHERING-005", "이미 참가 신청한 일정입니다.", HttpStatus.CONFLICT),

	/** 상품을 id로 찾지 못함(없거나 삭제됨). */
	GATHERING_PRODUCT_NOT_FOUND("GATHERING-006", "상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
}
