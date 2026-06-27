package com.org.meeple.core.report.command.application.port.`in`.command

import com.org.meeple.common.report.ReportType

/** 신고 생성 입력. 신고자(reporterId)는 인증 주체에서 따로 받으므로 여기 포함하지 않는다. */
data class CreateReportCommand(
	val type: ReportType,
	val chatRoomId: Long? = null,
	val toTeamId: Long? = null,
	val toUserId: Long? = null,
	val description: String? = null,
)
