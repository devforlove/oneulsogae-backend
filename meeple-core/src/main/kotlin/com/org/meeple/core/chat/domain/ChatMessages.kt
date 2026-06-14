package com.org.meeple.core.chat.domain

/**
 * 채팅방 메세지([ChatMessage]) 목록의 일급 컬렉션(first-class collection).
 * 원시 List를 그대로 노출하지 않고 감싸, 컬렉션에 대한 동작을 한곳에 응집시킨다.
 * 최근 메세지가 먼저 오도록(id 내림차순) 담기며, 더 과거를 이어 읽을 때 쓸 커서를 제공한다.
 */
data class ChatMessages(
	val values: List<ChatMessage>,
) {

	/** 메세지 개수. */
	val size: Int
		get() = values.size

	/** 비어 있는지 여부. */
	fun isEmpty(): Boolean = values.isEmpty()

	/** 다음(더 과거) 페이지 조회의 기준 커서. 가장 오래된(마지막) 메세지의 id이며, 비어 있으면 null. */
	val nextCursor: Long?
		get() = values.lastOrNull()?.id

	companion object {

		/** 빈 메세지 목록. */
		fun empty(): ChatMessages = ChatMessages(emptyList())
	}
}
