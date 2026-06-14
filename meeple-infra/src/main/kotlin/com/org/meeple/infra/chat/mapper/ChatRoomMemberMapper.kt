package com.org.meeple.infra.chat.mapper

import com.org.meeple.core.chat.domain.ChatRoomMember
import com.org.meeple.infra.chat.entity.ChatRoomMemberEntity

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
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun ChatRoomMember.toEntity(): ChatRoomMemberEntity =
	ChatRoomMemberEntity(
		chatRoomId = chatRoomId,
		userId = userId,
		unreadCount = unreadCount,
		lastReadAt = lastReadAt,
		joinedAt = joinedAt,
		exitedAt = exitedAt,
	).also { if (id != 0L) it.id = id }
