package com.org.meeple.core.chat.domain

/**
 * 한 채팅방의 참가자([ChatRoomMember]) 목록의 일급 컬렉션(first-class collection).
 * 원시 List를 그대로 노출하지 않고 감싸, 참가자 목록에 대한 동작을 한곳에 응집시킨다.
 * 1:1·그룹챗 모두 같은 모델을 쓰며, 주로 참가자 저장(SaveChatRoomMemberPort)과 상대 식별에 쓴다.
 * 나감([ChatRoomMember.isExited]) 여부는 DB 기록용 상태일 뿐이며, 상대 식별은 나감 여부로 거르지 않는다. (나간 참가자도 그대로 포함한다)
 * 프로필을 동반한 참가자 조회·참가 검증은 [ChatParticipants]가 담당한다.
 */
data class ChatRoomMembers(
	val values: List<ChatRoomMember>,
) {

	/** 참가자 수. */
	val size: Int
		get() = values.size

	/** 비어 있는지 여부. */
	fun isEmpty(): Boolean = values.isEmpty()

	/** [userId] 참가자를 찾는다. 없으면 null. */
	fun find(userId: Long): ChatRoomMember? =
		values.firstOrNull { it.userId == userId }

	/** [userId]를 제외한 나머지(상대방) 참가자들. (1:1 채팅이면 한 명, 그룹챗이면 여러 명, 나간 참가자도 포함) */
	fun partnersOf(userId: Long): List<ChatRoomMember> =
		values.filter { it.userId != userId }

	companion object {

		/** 빈 참가자 목록. */
		fun empty(): ChatRoomMembers = ChatRoomMembers(emptyList())
	}
}
