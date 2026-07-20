package com.org.oneulsogae.core.chat.command.application.port.out

import com.org.oneulsogae.core.chat.command.domain.ChatRoomMember
import com.org.oneulsogae.core.chat.command.domain.ChatRoomMembers
import java.time.LocalDateTime

/**
 * 채팅방 참가자 저장 아웃포트.
 * 신규 참가자를 저장하거나, 기존 참가자(id 존재)의 읽음 상태·퇴장 변경분을 반영한다.
 */
interface SaveChatRoomMemberPort {

	fun save(member: ChatRoomMember): ChatRoomMember

	/** 여러 참가자를 한 번에 저장한다. (채팅방 개설 시 두 참가자 동시 생성 등) */
	fun saveAll(members: ChatRoomMembers): ChatRoomMembers

	/**
	 * 참가자 [userId]의 안 읽은 개수(뱃지)만 0으로 되돌리고 마지막 읽음 시각을 [now]로 갱신한다. 갱신된 행 수를 반환한다(0이면 참가자 없음).
	 * 읽음 포인터(lastReadMessageId)는 건드리지 않는 타깃 UPDATE다 — 엔티티 전체 머지가 WS 읽음 전진을 덮어 포인터가 역행하던 문제를 막는다.
	 */
	fun resetUnreadCount(chatRoomId: Long, userId: Long, now: LocalDateTime): Int
}
