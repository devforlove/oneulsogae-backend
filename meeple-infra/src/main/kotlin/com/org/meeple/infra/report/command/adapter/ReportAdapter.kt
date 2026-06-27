package com.org.meeple.infra.report.command.adapter

import com.org.meeple.core.report.command.application.port.out.SaveReportPort
import com.org.meeple.core.report.command.domain.Report
import com.org.meeple.infra.report.command.mapper.toDomain
import com.org.meeple.infra.report.command.mapper.toEntity
import com.org.meeple.infra.report.command.repository.ReportJpaRepository
import org.springframework.stereotype.Component

/**
 * [ReportEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 신고 저장 out-port([SaveReportPort])를 구현한다.
 */
@Component
class ReportAdapter(
	private val reportJpaRepository: ReportJpaRepository,
) : SaveReportPort {

	override fun save(report: Report): Report =
		reportJpaRepository.save(report.toEntity()).toDomain()
}
