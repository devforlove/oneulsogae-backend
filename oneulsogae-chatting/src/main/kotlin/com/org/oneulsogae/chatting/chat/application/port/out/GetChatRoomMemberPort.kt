package com.org.oneulsogae.chatting.chat.application.port.out

/**
 * 채팅방 참가자 조회 아웃포트. (chatting 소유, infra 어댑터가 구현)
 * 발송 시 발신자 검증을 단건 존재로 가볍게 확인한다. (참가자 전체 로드 없이)
 */
interface GetChatRoomMemberPort {

	/** [userId]가 [chatRoomId] 채팅방의 참가자인지 존재 여부만 확인한다. */
	fun existsByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): Boolean
}
