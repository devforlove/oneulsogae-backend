package com.org.meeple.core.report.query.service.port.`in`

import com.org.meeple.core.report.query.dto.AdminReportPage

/** 어드민 신고 목록 조회 유스케이스. */
interface GetAdminReportsUseCase {

	/** 유저 신고를 최신순으로 [page](0부터)·[size] 단위 페이징 조회한다. */
	fun getReports(page: Int, size: Int): AdminReportPage
}
