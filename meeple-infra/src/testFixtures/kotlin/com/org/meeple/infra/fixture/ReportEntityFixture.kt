package com.org.meeple.infra.fixture

import com.org.meeple.common.report.ReportStatus
import com.org.meeple.common.report.ReportType
import com.org.meeple.infra.report.command.entity.ReportEntity

object ReportEntityFixture {

	fun create(
		type: ReportType = ReportType.ETC,
		fromUserId: Long = 1L,
		toUserId: Long? = 2L,
		description: String? = null,
		status: ReportStatus = ReportStatus.PENDING,
	): ReportEntity = ReportEntity(
		type = type,
		fromUserId = fromUserId,
		toUserId = toUserId,
		description = description,
		status = status,
	)
}
