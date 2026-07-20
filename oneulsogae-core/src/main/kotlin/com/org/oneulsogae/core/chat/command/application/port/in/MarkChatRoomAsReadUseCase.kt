package com.org.oneulsogae.core.chat.command.application.port.`in`

/**
 * 채팅방 읽음 처리 인포트(유스케이스).
 * [userId]가 [chatRoomId] 채팅방의 메세지를 모두 확인한 것으로 보고, 그 참가자의 안 읽은 개수를 0으로 초기화한다. (상태 변경 명령)
 * 조회자는 그 방의 참가자여야 한다.
 */
interface MarkChatRoomAsReadUseCase {

	fun markAsRead(userId: Long, chatRoomId: Long)
}
