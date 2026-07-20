package com.org.oneulsogae.common.report

/** 신고 처리 상태. 접수([PENDING])된 신고를 운영자가 확인하면 처리 완료([RESOLVED])로 바꾼다. */
enum class ReportStatus(val description: String) {
	PENDING("접수"),
	RESOLVED("처리 완료"),
}
