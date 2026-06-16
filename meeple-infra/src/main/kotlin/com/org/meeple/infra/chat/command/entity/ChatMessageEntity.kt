package com.org.meeple.infra.chat.command.entity

import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * 채팅방에서 오간 메세지 한 건의 영속성 엔티티.
 * 채팅방 행(json)에 묶지 않고 메세지마다 한 행으로 INSERT한다. (append 시 전체 재기록·동시 쓰기 유실 회피, 키셋 페이징 가능)
 * 복합 인덱스 (chat_room_id, id)로 특정 방의 최근 메세지 역방향 스캔/페이지네이션을 커버한다.
 * 도메인 로직은 [com.org.meeple.core.chat.command.domain.ChatMessage]에 정의한다. (엔티티는 상태만 보관한다)
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "chat_messages",
	indexes = [
		Index(name = "idx_chat_room_id_id", columnList = "chat_room_id, id"),
	],
)
class ChatMessageEntity(
	@Column(name = "chat_room_id", nullable = false)
	val chatRoomId: Long,

	@Column(name = "sender_id", nullable = false)
	val senderId: Long,

	@Column(name = "content", nullable = false, length = 1000)
	val content: String,

	/** 메세지를 보낸 시각. */
	@Column(name = "sent_at", nullable = false)
	val sentAt: LocalDateTime,
) : BaseEntity()
