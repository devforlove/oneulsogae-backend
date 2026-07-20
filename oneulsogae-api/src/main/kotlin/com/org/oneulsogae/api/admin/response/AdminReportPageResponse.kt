package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.report.query.dto.AdminReportPage

/** 어드민 신고 목록 페이지 응답. (offset 페이징) */
data class AdminReportPageResponse(
	val content: List<AdminReportSummaryResponse>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
	val hasNext: Boolean,
) {
	companion object {
		fun of(page: AdminReportPage): AdminReportPageResponse =
			AdminReportPageResponse(
				content = page.reports.values.map(AdminReportSummaryResponse::of),
				page = page.page,
				size = page.size,
				totalElements = page.totalElements,
				totalPages = page.totalPages,
				hasNext = page.hasNext,
			)
	}
}
