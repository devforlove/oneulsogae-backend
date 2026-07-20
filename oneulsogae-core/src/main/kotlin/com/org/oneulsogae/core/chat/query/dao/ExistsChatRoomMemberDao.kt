package com.org.oneulsogae.core.chat.query.dao

/**
 * 채팅방 참가자 존재 조회 리포지토리(query out-port 인터페이스). (접근 검증용, Spring Data 파생 쿼리로 충분)
 * 변경 대상 참가자 로드는 command 쪽 [com.org.oneulsogae.core.chat.command.application.port.out.GetChatRoomMemberPort]가,
 * 프로필 조인 조회는 [GetChatParticipantDao]가 담당한다.
 */
interface ExistsChatRoomMemberDao {

	/**
	 * [userId]가 [chatRoomId] 채팅방의 참가자인지 존재 여부만 확인한다. (프로필이 필요 없는 접근 검증용)
	 * (chat_room_id, user_id) 복합 유니크 인덱스로 단건 확인한다. 나감 여부는 보지 않는다.
	 */
	fun existsByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): Boolean
}
