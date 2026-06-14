package com.org.meeple.core.chat.application.port.out

/**
 * 채팅방 참가자 단순 조회 아웃포트. (Spring Data 파생 쿼리로 충분한 조회)
 * 프로필 조인이 필요한 참가자 조회는 QueryDSL 전용 포트([GetChatParticipantPort])로 분리돼 있다.
 */
interface GetChatRoomMemberPort {

	/**
	 * [userId]가 [chatRoomId] 채팅방의 참가자인지 존재 여부만 확인한다. (프로필이 필요 없는 접근 검증용)
	 * (chat_room_id, user_id) 복합 유니크 인덱스로 단건 확인한다. 나감 여부는 보지 않는다.
	 */
	fun existsByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): Boolean
}
