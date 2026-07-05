package com.org.meeple.admin.common.error

import org.springframework.http.HttpStatus

/**
 * 어드민 도메인 에러 코드. [AdminException]에 넘겨 사용한다.
 * (core의 ErrorCode/도메인 에러코드에 의존하지 않도록 admin이 자체 정의한다)
 */
enum class AdminErrorCode(
	val code: String,
	val message: String,
	val status: HttpStatus,
) {

	// 코드 문자열은 기존 신고 에러(REPORT-001)와 동일하게 유지해 응답 계약을 보존한다.
	REPORT_NOT_FOUND("REPORT-001", "신고를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	COMPANY_IMAGE_VERIFICATION_NOT_FOUND("COMPANY-IMAGE-001", "직장 인증을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	NOTICE_NOT_FOUND("NOTICE-001", "공지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	INQUIRY_NOT_FOUND("INQUIRY-001", "문의를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	INQUIRY_ALREADY_ANSWERED("INQUIRY-002", "이미 답변된 문의입니다.", HttpStatus.CONFLICT),

	// 모임 생성 입력 검증. 코드 문자열(GATHER-xxx)은 이동 전 계약을 그대로 보존한다.
	GATHERING_INVALID_TITLE("GATHER-001", "모임 제목은 필수입니다.", HttpStatus.BAD_REQUEST),
	GATHERING_TITLE_TOO_LONG("GATHER-002", "모임 제목은 100자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
	GATHERING_DESCRIPTION_TOO_LONG("GATHER-003", "모임 소개는 1000자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
	GATHERING_INVALID_CAPACITY("GATHER-004", "모임 정원은 최소 2명 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
	GATHERING_INVALID_GATHERING_AT("GATHER-005", "모임 일시는 현재 이후여야 합니다.", HttpStatus.BAD_REQUEST),
	GATHERING_INVALID_FEE("GATHER-006", "참가비는 0원 이상이어야 하며, 남/녀를 함께 입력해야 합니다.", HttpStatus.BAD_REQUEST),
	GATHERING_INVALID_REGION("GATHER-007", "모임 지역은 필수입니다.", HttpStatus.BAD_REQUEST),
}
