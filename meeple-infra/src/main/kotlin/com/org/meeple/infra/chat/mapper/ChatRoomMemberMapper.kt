package com.org.meeple.infra.chat.mapper

import com.org.meeple.core.chat.domain.ChatRoomMember
import com.org.meeple.infra.chat.entity.ChatRoomMemberEntity
import java.time.LocalDateTime

/** 영속성 엔티티 -> 도메인 모델 */
fun ChatRoomMemberEntity.toDomain(): ChatRoomMember =
	ChatRoomMember(
		id = id ?: 0,
		chatRoomId = chatRoomId,
		userId = userId,
		unreadCount = unreadCount,
		lastReadAt = lastReadAt,
		joinedAt = joinedAt,
		exitedAt = exitedAt,
		deletedAt = deletedAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 * deletedAt이 있으면 소프트 삭제 상태로 마킹한다. (deleted_at은 BaseEntity가 protected로 관리하므로 softDelete로 적용)
 */
fun ChatRoomMember.toEntity(): ChatRoomMemberEntity =
	ChatRoomMemberEntity(
		chatRoomId = chatRoomId,
		userId = userId,
		unreadCount = unreadCount,
		lastReadAt = lastReadAt,
		joinedAt = joinedAt,
		exitedAt = exitedAt,
	).also {
		if (id != 0L) it.id = id
		deletedAt?.let { at: LocalDateTime -> it.softDelete(at) }
	}
