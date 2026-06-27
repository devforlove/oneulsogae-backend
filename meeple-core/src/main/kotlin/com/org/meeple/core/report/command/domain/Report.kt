package com.org.meeple.core.report.command.domain

import com.org.meeple.common.report.ReportType

/**
 * 사용자 신고. 신고자([fromUserId])가 신고 대상(상대 팀 [toTeamId] 또는 상대 유저 [toUserId])을 사유([type])와 함께 신고한다.
 * 채팅 맥락에서의 신고면 [chatRoomId]를 함께 남긴다. 대상·채팅방·상세 설명은 상황에 따라 없을 수 있다.
 */
data class Report(
	val id: Long = 0,
	val type: ReportType,
	val fromUserId: Long,
	val chatRoomId: Long? = null,
	val toTeamId: Long? = null,
	val toUserId: Long? = null,
	val description: String? = null,
) {
	companion object {
		fun create(
			type: ReportType,
			fromUserId: Long,
			chatRoomId: Long? = null,
			toTeamId: Long? = null,
			toUserId: Long? = null,
			description: String? = null,
		): Report =
			Report(
				type = type,
				fromUserId = fromUserId,
				chatRoomId = chatRoomId,
				toTeamId = toTeamId,
				toUserId = toUserId,
				description = description,
			)
	}
}
