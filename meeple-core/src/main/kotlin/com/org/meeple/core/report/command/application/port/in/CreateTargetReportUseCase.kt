package com.org.meeple.core.report.command.application.port.`in`

import com.org.meeple.core.report.command.application.port.`in`.command.CreateTargetReportCommand
import com.org.meeple.core.report.command.domain.Report

interface CreateTargetReportUseCase {
	/** [reporterId]가 [command]의 대상(targetType+targetId)을 신고하고, 저장된 신고를 반환한다. */
	fun create(reporterId: Long, command: CreateTargetReportCommand): Report
}
