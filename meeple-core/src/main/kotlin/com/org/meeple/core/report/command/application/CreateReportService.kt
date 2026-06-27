package com.org.meeple.core.report.command.application

import com.org.meeple.core.report.command.application.port.`in`.CreateReportUseCase
import com.org.meeple.core.report.command.application.port.`in`.command.CreateReportCommand
import com.org.meeple.core.report.command.application.port.out.SaveReportPort
import com.org.meeple.core.report.command.domain.Report
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CreateReportUseCase] 구현. 신고자([reporterId])의 신고를 생성해 저장한다. (단순 저장 — 검증 없음)
 */
@Service
class CreateReportService(
	private val saveReportPort: SaveReportPort,
) : CreateReportUseCase {

	@Transactional
	override fun create(reporterId: Long, command: CreateReportCommand): Report {
		val report: Report = Report.create(
			type = command.type,
			fromUserId = reporterId,
			chatRoomId = command.chatRoomId,
			toTeamId = command.toTeamId,
			toUserId = command.toUserId,
			description = command.description,
		)
		return saveReportPort.save(report)
	}
}
