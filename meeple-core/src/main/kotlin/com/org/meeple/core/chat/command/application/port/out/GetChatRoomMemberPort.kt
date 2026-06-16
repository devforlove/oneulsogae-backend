package com.org.meeple.core.chat.command.application.port.out

import com.org.meeple.core.chat.command.domain.ChatRoomMember

/**
 * 채팅방 참가자 단순 조회 아웃포트. (명령 흐름에서 변경 대상 참가자를 로드하거나 종료 판정에 쓰는 Spring Data 파생 쿼리)
 * 접근 검증용 존재 조회는 query 쪽 [com.org.meeple.core.chat.query.dao.ChatRoomMemberQueryDao]가,
 * 프로필 조인이 필요한 참가자 조회는 [com.org.meeple.core.chat.query.dao.ChatParticipantQueryDao]가 담당한다.
 */
interface GetChatRoomMemberPort {

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
