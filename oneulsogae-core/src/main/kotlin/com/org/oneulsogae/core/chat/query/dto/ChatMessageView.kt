package com.org.oneulsogae.core.chat.query.dto

import com.org.oneulsogae.common.chat.ChatMessageType
import java.time.LocalDateTime

/**
 * 채팅 메세지 한 건의 read model. (상세 조회에서 표시·커서에 필요한 최소 컬럼만 담는 투영)
 * query는 명령 도메인([com.org.oneulsogae.core.chat.command.domain.ChatMessage])에 의존하지 않고 이 read model을 쓴다.
 * [type]이 SYSTEM(예: 상대방 나감 안내)이면 보낸 사람이 없어 [senderId]가 null이다.
 */
data class ChatMessageView(
	val id: Long,
	val senderId: Long?,
	val content: String,
	val type: ChatMessageType,
	val sentAt: LocalDateTime,
)
