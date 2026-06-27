package com.org.meeple.api.report.response

import com.org.meeple.core.report.command.domain.Report

data class ReportResponse(
	val reportId: Long,
) {
	companion object {
		fun of(report: Report): ReportResponse =
			ReportResponse(reportId = report.id)
	}
}
