package com.org.meeple.core.report.query.service

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.report.ReportErrorCode
import com.org.meeple.core.report.query.dao.GetAdminReportDao
import com.org.meeple.core.report.query.dto.AdminReportDetailView
import com.org.meeple.core.report.query.dto.AdminReportPage
import com.org.meeple.core.report.query.service.port.`in`.GetAdminReportsUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** [GetAdminReportsUseCase] 구현. (조회 전용) 유저 신고를 최신순 페이징 조회한다. */
@Service
@Transactional(readOnly = true)
class GetAdminReportsService(
	private val getAdminReportDao: GetAdminReportDao,
) : GetAdminReportsUseCase {

	override fun getReports(page: Int, size: Int): AdminReportPage {
		val pageNumber: Int = page.coerceAtLeast(0)
		val pageSize: Int = size.coerceIn(1, MAX_PAGE_SIZE)
		val offset: Long = pageNumber.toLong() * pageSize
		return AdminReportPage(
			reports = getAdminReportDao.findPage(offset, pageSize),
			page = pageNumber,
			size = pageSize,
			totalElements = getAdminReportDao.count(),
		)
	}

	override fun getReport(id: Long): AdminReportDetailView =
		getAdminReportDao.findDetailById(id)
			?: throw BusinessException(ReportErrorCode.REPORT_NOT_FOUND, "신고를 찾을 수 없습니다: $id")

	companion object {
		private const val MAX_PAGE_SIZE: Int = 100
	}
}
