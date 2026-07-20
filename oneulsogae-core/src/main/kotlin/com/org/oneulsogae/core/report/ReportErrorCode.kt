package com.org.oneulsogae.core.report

import com.org.oneulsogae.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/** 신고 도메인 에러 코드. [com.org.oneulsogae.core.common.error.BusinessException]에 넘겨 사용한다. */
enum class ReportErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	REPORT_NOT_FOUND("REPORT-001", "신고를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
}
