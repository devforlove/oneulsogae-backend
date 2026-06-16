package com.org.meeple.core.chat.command.application.port.`in`.command

/**
 * 채팅방 생성 입력. 어느 매칭에서 생성됐는지([matchId])와 참가자 userId 목록([participantUserIds])을 받는다.
 * 참가자는 방이 아니라 참가자(ChatRoomMember) 단위로 보관되므로 목록으로 받는다. (1:1이면 두 명, 그룹챗이면 여러 명)
 * 만료 시각/초기 상태는 도메인([com.org.meeple.core.chat.command.domain.ChatRoom.open])이 정하므로 받지 않는다.
 */
data class SaveChatRoomCommand(
	val matchId: Long,
	val participantUserIds: List<Long>,
)
