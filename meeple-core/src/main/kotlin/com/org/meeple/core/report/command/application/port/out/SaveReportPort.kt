package com.org.meeple.core.report.command.application.port.out

import com.org.meeple.core.report.command.domain.Report

/** 신고 저장 out-port. infra 어댑터가 구현한다. */
interface SaveReportPort {
	fun save(report: Report): Report
}
