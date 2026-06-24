package com.org.meeple.infra.chat.command.mapper

import com.org.meeple.core.chat.command.domain.ChatRoom
import com.org.meeple.infra.chat.command.entity.ChatRoomEntity
import java.time.LocalDateTime

/** 영속성 엔티티 -> 도메인 모델 */
fun ChatRoomEntity.toDomain(): ChatRoom =
	ChatRoom(
		id = id ?: 0,
		matchType = matchType,
		matchId = matchId,
		status = status,
		expiredAt = expiredAt,
		lastMessage = lastMessage,
		lastMessageAt = lastMessageAt,
		deletedAt = deletedAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 * deletedAt이 있으면 소프트 삭제 상태로 마킹한다. (deleted_at은 BaseEntity가 protected로 관리하므로 softDelete로 적용)
 */
fun ChatRoom.toEntity(): ChatRoomEntity =
	ChatRoomEntity(
		matchType = matchType,
		matchId = matchId,
		status = status,
		expiredAt = expiredAt,
		lastMessage = lastMessage,
		lastMessageAt = lastMessageAt,
	).also {
		if (id != 0L) it.id = id
		deletedAt?.let { at: LocalDateTime -> it.softDelete(at) }
	}
