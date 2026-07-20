package com.org.oneulsogae.chatting.chat.application.port.out

/**
 * 안 읽은 개수 증가 아웃포트. (chatting 소유, infra 어댑터가 구현)
 * 참가자를 도메인으로 로드하지 않고, 발신자를 제외한 나머지 참가자의 unread_count를 **벌크 UPDATE 한 번**으로 올린다. (인원수 무관, N+1 제거)
 */
interface IncreaseUnreadCountPort {

	/** [chatRoomId] 방에서 [senderId]를 제외한 참가자들의 안 읽은 개수를 1 증가시킨다. */
	fun increaseForOthers(chatRoomId: Long, senderId: Long)
}
