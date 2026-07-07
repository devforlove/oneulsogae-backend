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
	GATHERING_INVALID_MIN_PARTICIPANTS("GATHER-004", "모임 최소 인원은 2명 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
	GATHERING_INVALID_FEE("GATHER-006", "참가비는 0원 이상이어야 하며, 남/녀를 함께 입력해야 합니다.", HttpStatus.BAD_REQUEST),
	GATHERING_INVALID_REGION("GATHER-007", "모임 지역은 필수입니다.", HttpStatus.BAD_REQUEST),
	GATHERING_NOT_FOUND("GATHER-008", "모임을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	GATHERING_INVALID_IMAGE_TYPE("GATHER-009", "모임 대표 이미지는 비어 있지 않은 JPEG 또는 PNG 파일이어야 합니다.", HttpStatus.BAD_REQUEST),
	GATHERING_IMAGE_TOO_LARGE("GATHER-010", "모임 대표 이미지는 5MB 이하여야 합니다.", HttpStatus.BAD_REQUEST),
	GATHERING_INVALID_MAX_PARTICIPANTS("GATHER-011", "모임 최대 인원은 최소 인원 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
	GATHERING_INVALID_EARLY_BIRD_CAPACITY("GATHER-012", "얼리버드 적용 인원은 얼리버드 할인율과 함께 1명 이상 최대 인원 이하로 입력해야 합니다.", HttpStatus.BAD_REQUEST),
	GATHERING_INVALID_STATUS_TRANSITION("GATHER-013", "요청한 상태로 전이할 수 없습니다.", HttpStatus.CONFLICT),

	// 모임 일정(GatheringSchedule) 관련.
	GATHERING_SCHEDULE_NOT_FOUND("GATHER-014", "모임 일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	GATHERING_SCHEDULE_INVALID_START_AT("GATHER-015", "일정 시작 시각은 현재 이후여야 합니다.", HttpStatus.BAD_REQUEST),
	GATHERING_SCHEDULE_INVALID_END_AT("GATHER-016", "일정 종료 시각은 시작 시각 이후여야 합니다.", HttpStatus.BAD_REQUEST),
	GATHERING_SCHEDULE_INVALID_STATUS_TRANSITION("GATHER-017", "요청한 일정 상태로 전이할 수 없습니다.", HttpStatus.CONFLICT),
	GATHERING_INVALID_EARLY_BIRD_DISCOUNT_RATE("GATHER-018", "얼리버드 할인율은 1 이상 100 이하여야 합니다.", HttpStatus.BAD_REQUEST),
}
