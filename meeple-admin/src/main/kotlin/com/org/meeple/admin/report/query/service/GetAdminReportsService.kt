package com.org.meeple.admin.report.query.service

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.report.query.dao.GetAdminReportDao
import com.org.meeple.admin.report.query.dto.AdminReportDetailView
import com.org.meeple.admin.report.query.dto.AdminReportPage
import com.org.meeple.admin.report.query.service.port.`in`.GetAdminReportsUseCase
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
			?: throw AdminException(AdminErrorCode.REPORT_NOT_FOUND, "신고를 찾을 수 없습니다: $id")

	companion object {
		private const val MAX_PAGE_SIZE: Int = 100
	}
}
