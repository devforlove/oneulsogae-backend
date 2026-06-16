package com.org.meeple.core.chat.query.dto

import java.time.LocalDateTime

/**
 * 채팅 메세지 한 건의 read model. (상세 조회에서 표시·커서에 필요한 최소 컬럼만 담는 투영)
 * query는 명령 도메인([com.org.meeple.core.chat.command.domain.ChatMessage])에 의존하지 않고 이 read model을 쓴다.
 */
data class ChatMessageView(
	val id: Long,
	val senderId: Long,
	val content: String,
	val sentAt: LocalDateTime,
)
