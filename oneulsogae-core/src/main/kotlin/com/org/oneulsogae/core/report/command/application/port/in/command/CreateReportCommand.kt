package com.org.oneulsogae.core.report.command.application.port.`in`.command

import com.org.oneulsogae.common.report.ReportType

/**
 * 신고 생성 입력. 신고자(reporterId)는 인증 주체에서 따로 받으므로 여기 포함하지 않는다.
 * 신고 대상은 직접 받지 않고, [chatRoomId]로 채팅방의 매칭 정보(matchType+matchId)를 얻어 매칭 종류에 따라 상대 유저/팀 자리에 채운다.
 */
data class CreateReportCommand(
	val type: ReportType,
	val chatRoomId: Long,
	val description: String? = null,
)
