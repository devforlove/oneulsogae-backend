package com.org.meeple.core.chat.application.port.out

import com.org.meeple.core.chat.domain.ChatRoomMember

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

	/**
	 * [userId]의 [chatRoomId] 채팅방 참가자 행을 단건 조회한다. 없으면 null.
	 * 읽음 처리 등 참가자 본인의 상태를 바꾸기 위해 로드할 때 쓴다. (chat_room_id, user_id) 복합 유니크 인덱스로 단건 조회한다.
	 */
	fun findByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): ChatRoomMember?

	/**
	 * [chatRoomId] 채팅방의 (소프트 삭제되지 않은) 참가자 수를 센다.
	 * 나가기에서 "남은 참가자가 있는지"로 방 종료 여부를 정하는 데 쓴다. (@SQLRestriction으로 deleted_at이 채워진 행은 제외된다)
	 */
	fun countByChatRoomId(chatRoomId: Long): Long
}
