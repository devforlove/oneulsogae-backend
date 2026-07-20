package com.org.oneulsogae.core.chat.command.application.port.`in`

/**
 * 채팅방 나가기 인포트(유스케이스).
 * [userId]가 [chatRoomId] 채팅방에서 나간다. 해당 사용자의 참가자([com.org.oneulsogae.core.chat.command.domain.ChatRoomMember]) 행을 소프트 삭제해
 * 그 사용자의 채팅방 목록·접근에서 제외시킨다. (상태 변경 명령)
 * 남은 참가자가 없으면(마지막 한 명이 나가면) 채팅방을 종료(CLOSED) 상태로 전이한다. (그룹챗에서 다른 참가자가 남아 있으면 방은 유지)
 * 나가려는 사용자는 그 방의 참가자여야 한다.
 */
interface LeaveChatRoomUseCase {

	fun leave(userId: Long, chatRoomId: Long)
}
