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
}
