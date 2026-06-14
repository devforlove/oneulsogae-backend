package com.org.meeple.core.chat.application.port.out

import com.org.meeple.core.chat.domain.ChatMessages

/**
 * 채팅 메세지 조회 아웃포트.
 * 일급 컬렉션([ChatMessages])으로 반환하며, 실제 구현은 infra 레이어의 어댑터가 담당한다.
 */
interface GetChatMessagePort {

	/**
	 * [chatRoomId] 채팅방의 메세지를 최근(id 내림차순) 순으로 [limit]건 조회한다.
	 * [beforeMessageId]가 null이면 최신부터, 값이 있으면 그 id보다 과거(더 작은 id) 구간을 조회한다. (키셋 페이지네이션)
	 * `(chat_room_id, id)` 인덱스 역방향 스캔이라 filesort 없이 끊어 읽는다.
	 */
	fun findByChatRoom(chatRoomId: Long, beforeMessageId: Long?, limit: Int): ChatMessages
}
