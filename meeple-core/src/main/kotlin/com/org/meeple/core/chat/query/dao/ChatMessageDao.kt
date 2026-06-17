package com.org.meeple.core.chat.query.dao

import com.org.meeple.core.chat.query.dto.ChatMessageViews

/**
 * 채팅 메세지 조회 리포지토리(query out-port 인터페이스).
 * 일급 컬렉션([ChatMessageViews])으로 반환하며, 실제 구현은 infra 레이어의 어댑터가 담당한다.
 */
interface ChatMessageDao {

	/**
	 * [chatRoomId] 채팅방의 최근 [limit]건을 조회해 id 오름차순(과거→최신)으로 담아 반환한다. (최신 N건을 위해 쿼리는 DESC로 끊어 읽고 컬렉션이 ASC로 보정)
	 * [beforeMessageId]가 null이면 최신부터, 값이 있으면 그 id보다 과거(더 작은 id) 구간을 조회한다. (키셋 페이지네이션)
	 * `(chat_room_id, id)` 인덱스 역방향 스캔이라 filesort 없이 끊어 읽는다.
	 */
	fun findByChatRoom(chatRoomId: Long, beforeMessageId: Long?, limit: Int): ChatMessageViews
}
