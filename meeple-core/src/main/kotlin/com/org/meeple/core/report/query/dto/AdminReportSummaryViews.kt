package com.org.meeple.core.report.query.dto

/** 어드민 신고 목록 read model 일급 컬렉션. */
data class AdminReportSummaryViews(
	val values: List<AdminReportSummaryView>,
) {
	companion object {
		fun empty(): AdminReportSummaryViews = AdminReportSummaryViews(emptyList())
	}
}
