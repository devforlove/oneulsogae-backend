package com.org.meeple.core.chat.command.application.port.out

import com.org.meeple.core.chat.command.domain.ChatRoom

/**
 * 채팅방 조회 아웃포트. (명령 흐름에서 변경 대상 애그리거트를 로드하는 단건 조회)
 * 도메인 모델([ChatRoom])을 반환하며, 실제 구현은 infra 레이어의 어댑터가 담당한다.
 * 목록(read model) 조회는 query 쪽 [com.org.meeple.core.chat.query.dao.GetChatRoomDao]가 담당한다.
 */
interface GetChatRoomPort {

	/** id로 채팅방을 조회한다. 없으면 null. (참가자 검증·상세 조회용) */
	fun findById(chatRoomId: Long): ChatRoom?

	/** 매칭 id로 채팅방을 조회한다. 없으면 null. (매칭당 1개이므로 단건, 멱등 생성 시 중복 방지용) */
	fun findByMatchId(matchId: Long): ChatRoom?
}
