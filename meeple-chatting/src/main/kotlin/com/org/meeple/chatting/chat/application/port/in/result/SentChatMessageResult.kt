package com.org.meeple.chatting.chat.application.port.`in`.result

import com.org.meeple.common.chat.ChatMessageType
import java.time.LocalDateTime

/**
 * 채팅 메세지 발송 결과(리드 모델).
 * 유스케이스가 도메인 모델([com.org.meeple.chatting.chat.domain.ChatMessage]) 대신 이 결과 모델을 반환해,
 * 어댑터(컨트롤러)가 도메인에 직접 의존하지 않게 한다. 브로드캐스트 DTO는 이 결과로부터 만든다.
 * [type]이 SYSTEM(예: 상대방 나감 안내)이면 보낸 사람이 없어 [senderId]가 null이다.
 */
data class SentChatMessageResult(
	val id: Long,
	val chatRoomId: Long,
	val senderId: Long?,
	val content: String,
	val type: ChatMessageType,
	val sentAt: LocalDateTime,
)
