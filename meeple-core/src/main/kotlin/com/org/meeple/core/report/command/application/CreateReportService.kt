package com.org.meeple.core.report.command.application

import com.org.meeple.common.chat.ChatRoomMatchType
import com.org.meeple.common.report.ReportTargetType
import com.org.meeple.core.chat.command.application.port.`in`.GetChatRoomMatchUseCase
import com.org.meeple.core.chat.command.application.port.`in`.result.ChatRoomMatch
import com.org.meeple.core.report.command.application.port.`in`.CreateReportUseCase
import com.org.meeple.core.report.command.application.port.`in`.command.CreateReportCommand
import com.org.meeple.core.report.command.application.port.out.SaveReportPort
import com.org.meeple.core.report.command.domain.Report
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CreateReportUseCase] 구현. 신고자([reporterId])의 신고를 생성해 저장한다.
 * 신고 대상은 요청에서 직접 받지 않고, [chatRoomId]로 chat 도메인 in-port([GetChatRoomMatchUseCase])에서 매칭 정보(matchType+matchId)를 얻어
 * 매칭 종류에 따라 상대 유저(SOLO)/상대 팀(TEAM) 자리에 채운다. ([Report.create]) (채팅방이 없으면 GetChatRoomMatch가 거절한다)
 */
@Service
class CreateReportService(
	private val saveReportPort: SaveReportPort,
	private val getChatRoomMatchUseCase: GetChatRoomMatchUseCase,
) : CreateReportUseCase {

	@Transactional
	override fun create(reporterId: Long, command: CreateReportCommand): Report {
		val chatRoomMatch: ChatRoomMatch = getChatRoomMatchUseCase.getMatch(command.chatRoomId)
		val targetType: ReportTargetType = when (chatRoomMatch.matchType) {
			ChatRoomMatchType.SOLO -> ReportTargetType.USER
			ChatRoomMatchType.TEAM -> ReportTargetType.TEAM
		}
		val report: Report = Report.create(
			type = command.type,
			fromUserId = reporterId,
			targetType = targetType,
			targetId = chatRoomMatch.matchId,
			chatRoomId = command.chatRoomId,
			description = command.description,
		)
		return saveReportPort.save(report)
	}
}
