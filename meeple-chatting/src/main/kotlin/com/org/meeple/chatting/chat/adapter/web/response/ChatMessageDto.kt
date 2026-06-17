package com.org.meeple.chatting.chat.adapter.web.response

import com.org.meeple.chatting.chat.application.port.`in`.result.SentChatMessageResult
import com.org.meeple.common.chat.ChatMessageType
import java.time.LocalDateTime

/**
 * 채팅방 구독자에게 브로드캐스트하는 메세지 표현(DTO).
 * 유스케이스 결과([SentChatMessageResult])를 클라이언트 표시에 필요한 형태로 투영한다.
 * 본문(message)·보낸이(senderId)·보낸시각(createdAt) 외에, 클라이언트 정렬·중복제거에 쓰는 messageId와
 * 여러 방 구독 시 라우팅에 쓰는 chatRoomId를 함께 내려준다.
 * [type]이 SYSTEM(예: 상대방 나감 안내)이면 보낸 사람이 없어 [senderId]가 null이며, 클라이언트는 시스템 메세지로 렌더한다.
 */
data class ChatMessageDto(
	/** 저장된 메세지 식별자. 클라이언트 정렬·중복 제거·읽음 처리 기준. */
	val messageId: Long,
	/** 어느 채팅방의 메세지인지. (여러 방을 구독한 클라이언트의 라우팅용) */
	val chatRoomId: Long,
	/** 보낸 사람(userId). SYSTEM 메세지는 null. */
	val senderId: Long?,
	/** 메세지 본문. */
	val message: String,
	/** 메세지 유형. (일반 USER / 시스템 SYSTEM) */
	val type: ChatMessageType,
	/** 보낸 시각. */
	val createdAt: LocalDateTime,
) {

	companion object {

		/** 발송 결과([SentChatMessageResult])를 브로드캐스트 DTO로 변환한다. */
		fun from(result: SentChatMessageResult): ChatMessageDto =
			ChatMessageDto(
				messageId = result.id,
				chatRoomId = result.chatRoomId,
				senderId = result.senderId,
				message = result.content,
				type = result.type,
				createdAt = result.sentAt,
			)
	}
}
