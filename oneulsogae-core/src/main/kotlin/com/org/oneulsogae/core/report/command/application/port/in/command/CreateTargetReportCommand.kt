package com.org.oneulsogae.core.report.command.application.port.`in`.command

import com.org.oneulsogae.common.report.ReportTargetType
import com.org.oneulsogae.common.report.ReportType

/**
 * 대상 직접 지정 신고 입력. 신고자(reporterId)는 인증 주체에서 따로 받으므로 여기 포함하지 않는다.
 * 채팅방 맥락 없이 [targetType](USER/TEAM)과 [targetId]로 신고 대상을 직접 지정한다.
 */
data class CreateTargetReportCommand(
	val type: ReportType,
	val targetType: ReportTargetType,
	val targetId: Long,
	val description: String? = null,
)
