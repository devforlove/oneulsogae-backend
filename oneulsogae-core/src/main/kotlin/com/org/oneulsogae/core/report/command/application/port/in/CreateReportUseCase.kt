package com.org.oneulsogae.core.report.command.application.port.`in`

import com.org.oneulsogae.core.report.command.application.port.`in`.command.CreateReportCommand
import com.org.oneulsogae.core.report.command.domain.Report

interface CreateReportUseCase {
	/** [reporterId]가 [command] 내용으로 신고를 생성하고, 저장된 신고를 반환한다. */
	fun create(reporterId: Long, command: CreateReportCommand): Report
}
