package com.org.meeple.core.report.command.application

import com.org.meeple.common.report.ReportTargetType
import com.org.meeple.core.report.command.application.port.`in`.CreateTargetReportUseCase
import com.org.meeple.core.report.command.application.port.`in`.command.CreateTargetReportCommand
import com.org.meeple.core.report.command.application.port.out.SaveReportPort
import com.org.meeple.core.report.command.domain.Report
import com.org.meeple.core.teammatch.command.application.port.`in`.GetTeamByIdUseCase
import com.org.meeple.core.user.query.service.port.`in`.GetUserByIdUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CreateTargetReportUseCase] 구현. 채팅방 없이 요청의 대상(targetType+targetId)으로 신고를 생성해 저장한다.
 * 저장 전 대상이 실제 존재하는지 각 도메인 in-port로 검증한다.
 * USER면 user 도메인([GetUserByIdUseCase], 없으면 USER-001), TEAM이면 teammatch 도메인([GetTeamByIdUseCase], 없으면 TEAM-005).
 */
@Service
class CreateTargetReportService(
	private val saveReportPort: SaveReportPort,
	private val getUserByIdUseCase: GetUserByIdUseCase,
	private val getTeamByIdUseCase: GetTeamByIdUseCase,
) : CreateTargetReportUseCase {

	@Transactional
	override fun create(reporterId: Long, command: CreateTargetReportCommand): Report {
		validateTargetExists(command.targetType, command.targetId)
		val report: Report = Report.create(
			type = command.type,
			fromUserId = reporterId,
			targetType = command.targetType,
			targetId = command.targetId,
			description = command.description,
		)
		return saveReportPort.save(report)
	}

	// 대상이 실제 존재하는지 각 도메인 in-port로 확인한다. 없으면 해당 도메인 에러를 던진다.
	private fun validateTargetExists(targetType: ReportTargetType, targetId: Long) {
		when (targetType) {
			ReportTargetType.USER -> getUserByIdUseCase.getById(targetId)
			ReportTargetType.TEAM -> getTeamByIdUseCase.getById(targetId)
		}
	}
}
