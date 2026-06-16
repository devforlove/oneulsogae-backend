package com.org.meeple.core.chat.command.application.port.out

import com.org.meeple.core.chat.command.domain.ChatRoomMember
import com.org.meeple.core.chat.command.domain.ChatRoomMembers

/**
 * 채팅방 참가자 저장 아웃포트.
 * 신규 참가자를 저장하거나, 기존 참가자(id 존재)의 읽음 상태·퇴장 변경분을 반영한다.
 */
interface SaveChatRoomMemberPort {

	fun save(member: ChatRoomMember): ChatRoomMember

	/** 여러 참가자를 한 번에 저장한다. (채팅방 개설 시 두 참가자 동시 생성 등) */
	fun saveAll(members: ChatRoomMembers): ChatRoomMembers
}
