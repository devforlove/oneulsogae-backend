package com.org.oneulsogae.core.chat.command.application.port.`in`.result

import com.org.oneulsogae.common.chat.ChatRoomMatchType

/**
 * 채팅방이 어느 매칭에서 생성됐는지. (matchType=SOLO/TEAM, matchId=solo_matches.id 또는 team_matches.id)
 * 신고 생성처럼 다른 도메인이 채팅방의 매칭 정보를 알아야 할 때 chat in-port가 돌려준다.
 */
data class ChatRoomMatch(
	val matchType: ChatRoomMatchType,
	val matchId: Long,
)
