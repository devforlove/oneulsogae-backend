package com.org.meeple.core.chat.query.dto

/**
 * 채팅방 메세지([ChatMessageView]) 목록의 일급 컬렉션(first-class collection).
 * 원시 List를 그대로 노출하지 않고 감싸, 컬렉션에 대한 동작을 한곳에 응집시킨다.
 * 생성 시 항상 **id 오름차순(과거→최신)** 으로 정렬해 담는다. (조회 쿼리는 "최신 N건"을 위해 DESC로 가져오지만, 이 컬렉션이 ASC로 보정한다)
 * 더 과거를 이어 읽을 때 쓸 커서([nextCursor])를 제공한다.
 */
class ChatMessageViews(
	values: List<ChatMessageView>,
) {

	/** id 오름차순(과거→최신)으로 정렬된 메세지 목록. */
	val values: List<ChatMessageView> = values.sortedBy { it.id }

	/** 메세지 개수. */
	val size: Int
		get() = values.size

	/** 비어 있는지 여부. */
	fun isEmpty(): Boolean = values.isEmpty()

	/** 다음(더 과거) 페이지 조회의 기준 커서. 가장 오래된(첫) 메세지의 id이며, 비어 있으면 null. */
	val nextCursor: Long?
		get() = values.firstOrNull()?.id

	companion object {

		/** 빈 메세지 목록. */
		fun empty(): ChatMessageViews = ChatMessageViews(emptyList())
	}
}
