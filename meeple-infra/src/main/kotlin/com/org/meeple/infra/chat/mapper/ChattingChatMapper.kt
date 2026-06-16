package com.org.meeple.infra.chat.mapper

import com.org.meeple.chatting.chat.domain.ChatMessage
import com.org.meeple.infra.chat.entity.ChatMessageEntity

/**
 * 엔티티 ↔ chatting 모듈 도메인 변환.
 * 같은 chat 엔티티를 core도 쓰지만(`toDomain`/`toEntity`), chatting은 core에 의존하지 않는 자체 도메인을 쓰므로 이름을 구분한다.
 * 방·참가자는 발송 경로가 도메인으로 로드하지 않고 타깃 UPDATE로 처리하므로(메세지만 도메인으로 저장) ChatMessage 변환만 둔다.
 */

fun ChatMessageEntity.toChattingDomain(): ChatMessage =
	ChatMessage(
		id = id ?: 0,
		chatRoomId = chatRoomId,
		senderId = senderId,
		content = content,
		sentAt = sentAt,
	)

fun ChatMessage.toChattingEntity(): ChatMessageEntity =
	ChatMessageEntity(
		chatRoomId = chatRoomId,
		senderId = senderId,
		content = content,
		sentAt = sentAt,
	).also { if (id != 0L) it.id = id }
