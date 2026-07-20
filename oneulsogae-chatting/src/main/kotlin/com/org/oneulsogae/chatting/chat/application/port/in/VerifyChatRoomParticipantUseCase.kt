package com.org.oneulsogae.chatting.chat.application.port.`in`

/**
 * 채팅방 참가자 검증 인포트(유스케이스).
 * WebSocket 구독(SUBSCRIBE) 인가 등에서 사용자가 해당 방의 참가자인지 가볍게 검증한다. (단건 존재 확인)
 */
interface VerifyChatRoomParticipantUseCase {

	/** [userId]가 [chatRoomId] 채팅방의 참가자가 아니면 예외를 던진다. */
	fun verifyParticipant(userId: Long, chatRoomId: Long)
}
