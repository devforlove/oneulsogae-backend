package com.org.meeple.admin.report.query.dto

/**
 * 어드민 신고 목록 한 페이지(read model). offset(page·size) 페이징 결과.
 * [totalElements]는 (유저 신고 한정) 전체 개수, [totalPages]/[hasNext]는 파생값.
 */
data class AdminReportPage(
	val reports: AdminReportSummaryViews,
	val page: Int,
	val size: Int,
	val totalElements: Long,
) {
	val totalPages: Int
		get() = if (size <= 0) 0 else ((totalElements + size - 1) / size).toInt()

	val hasNext: Boolean
		get() = (page + 1).toLong() * size < totalElements

	companion object {
		fun empty(page: Int, size: Int): AdminReportPage =
			AdminReportPage(AdminReportSummaryViews.empty(), page, size, 0)
	}
}
