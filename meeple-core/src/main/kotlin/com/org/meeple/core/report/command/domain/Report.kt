package com.org.meeple.core.report.command.domain

import com.org.meeple.common.chat.ChatRoomMatchType
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
		/**
		 * 매칭 종류([matchType])에 따라 신고 대상 id([targetId])를 알맞은 자리에 채워 신고를 만든다.
		 * SOLO면 상대 유저([toUserId]), TEAM이면 상대 팀([toTeamId])에 [targetId]를 넣는다.
		 */
		fun create(
			type: ReportType,
			fromUserId: Long,
			matchType: ChatRoomMatchType,
			targetId: Long,
			chatRoomId: Long? = null,
			description: String? = null,
		): Report =
			Report(
				type = type,
				fromUserId = fromUserId,
				chatRoomId = chatRoomId,
				toUserId = if (matchType == ChatRoomMatchType.SOLO) targetId else null,
				toTeamId = if (matchType == ChatRoomMatchType.TEAM) targetId else null,
				description = description,
			)
	}
}
