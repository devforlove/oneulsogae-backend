package com.org.meeple.infra.chat.command.mapper

import com.org.meeple.core.chat.command.domain.ChatRoom
import com.org.meeple.infra.chat.command.entity.ChatRoomEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun ChatRoomEntity.toDomain(): ChatRoom =
	ChatRoom(
		id = id ?: 0,
		matchId = matchId,
		status = status,
		expiredAt = expiredAt,
		lastMessage = lastMessage,
		lastMessageAt = lastMessageAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun ChatRoom.toEntity(): ChatRoomEntity =
	ChatRoomEntity(
		matchId = matchId,
		status = status,
		expiredAt = expiredAt,
		lastMessage = lastMessage,
		lastMessageAt = lastMessageAt,
	).also { if (id != 0L) it.id = id }
