package com.org.oneulsogae.chatting.chat.adapter.web.response

import com.org.oneulsogae.chatting.chat.application.port.`in`.result.MarkMessagesAsReadResult

/**
 * 방 구독자에게 브로드캐스트하는 읽음 이벤트(DTO).
 * [readerId]가 [chatRoomId] 방에서 [lastReadMessageId]까지 읽었음을 알린다.
 * 클라이언트는 readerId의 읽음 포인터를 이 값으로 갱신하고, id가 [lastReadMessageId] 이하인 말풍선들의 "안 읽은 사람 수"를 다시 계산한다.
 */
data class MessageReadDto(
	val chatRoomId: Long,
	val readerId: Long,
	val lastReadMessageId: Long,
) {

	companion object {

		/** 읽음 처리 결과([MarkMessagesAsReadResult])를 브로드캐스트 DTO로 변환한다. */
		fun from(result: MarkMessagesAsReadResult): MessageReadDto =
			MessageReadDto(
				chatRoomId = result.chatRoomId,
				readerId = result.readerId,
				lastReadMessageId = result.lastReadMessageId,
			)
	}
}
