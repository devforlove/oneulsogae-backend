package com.org.meeple.core.chat.command.application.port.out

import com.org.meeple.common.chat.ChatRoomMatchType
import com.org.meeple.core.chat.command.domain.ChatRoom

/**
 * 채팅방 조회 아웃포트. (명령 흐름에서 변경 대상 애그리거트를 로드하는 단건 조회)
 * 도메인 모델([ChatRoom])을 반환하며, 실제 구현은 infra 레이어의 어댑터가 담당한다.
 * 목록(read model) 조회는 query 쪽 [com.org.meeple.core.chat.query.dao.GetChatRoomDao]가 담당한다.
 */
interface GetChatRoomPort {

	/** id로 채팅방을 조회한다. 없으면 null. (참가자 검증·상세 조회용) */
	fun findById(chatRoomId: Long): ChatRoom?

	/**
	 * 변경 흐름에서 방을 비관적 쓰기 락으로 조회한다. 없으면 null. (SELECT ... FOR UPDATE)
	 * 한 방에 대한 동시 변경을 방 행 기준으로 직렬화해 데드락을 막는다. (방 락을 가장 먼저 잡는 게이트)
	 */
	fun findByIdForUpdate(chatRoomId: Long): ChatRoom?

	/** 매칭 타입+id로 채팅방을 조회한다. 없으면 null. (타입별 매칭당 1개이므로 단건, 멱등 생성 시 중복 방지용) */
	fun findByMatchTypeAndMatchId(matchType: ChatRoomMatchType, matchId: Long): ChatRoom?
}
