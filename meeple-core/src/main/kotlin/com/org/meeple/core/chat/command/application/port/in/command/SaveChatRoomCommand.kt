package com.org.meeple.core.chat.command.application.port.`in`.command

import com.org.meeple.common.chat.ChatRoomMatchType

/**
 * 채팅방 생성 입력. 어느 매칭에서 생성됐는지([matchType]+[matchId])와 참가자 목록([participants])을 받는다.
 * [matchType]은 solo/team 매칭을 구분하는 판별값이다. (match_id가 두 매칭의 id를 함께 가리키므로 타입으로 구분)
 * 참가자는 방이 아니라 참가자(ChatRoomMember) 단위로 보관되므로 목록으로 받는다. (1:1이면 두 명, 그룹챗이면 여러 명)
 * 각 참가자는 TEAM 방에서 소속 팀([SaveChatRoomParticipant.teamId])을 함께 싣는다. (SOLO는 null) 목록 조회의 상대 팀 판별에 쓰인다.
 * 만료 시각/초기 상태는 도메인([com.org.meeple.core.chat.command.domain.ChatRoom.open])이 정하므로 받지 않는다.
 */
data class SaveChatRoomCommand(
	val matchType: ChatRoomMatchType,
	val matchId: Long,
	val participants: List<SaveChatRoomParticipant>,
)

/** 채팅방 참가자 한 명의 생성 입력. [teamId]는 TEAM 방의 소속 팀 id이며 SOLO 방은 null이다. */
data class SaveChatRoomParticipant(
	val userId: Long,
	val teamId: Long?,
)
