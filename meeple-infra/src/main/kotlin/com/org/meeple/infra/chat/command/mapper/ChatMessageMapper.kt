package com.org.meeple.infra.chat.command.mapper

import com.org.meeple.core.chat.command.domain.ChatMessage
import com.org.meeple.infra.chat.command.entity.ChatMessageEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun ChatMessageEntity.toDomain(): ChatMessage =
	ChatMessage(
		id = id ?: 0,
		chatRoomId = chatRoomId,
		senderId = senderId,
		content = content,
		type = type,
		sentAt = sentAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun ChatMessage.toEntity(): ChatMessageEntity =
	ChatMessageEntity(
		chatRoomId = chatRoomId,
		senderId = senderId,
		content = content,
		type = type,
		sentAt = sentAt,
	).also { if (id != 0L) it.id = id }
