package com.org.oneulsogae.infra.chat.command.entity

import com.org.oneulsogae.common.chat.ChatMessageType
import com.org.oneulsogae.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * 채팅방에서 오간 메세지 한 건의 영속성 엔티티.
 * 채팅방 행(json)에 묶지 않고 메세지마다 한 행으로 INSERT한다. (append 시 전체 재기록·동시 쓰기 유실 회피, 키셋 페이징 가능)
 * 복합 인덱스 (chat_room_id, id)로 특정 방의 최근 메세지 역방향 스캔/페이지네이션을 커버한다.
 * [type]이 SYSTEM(예: 상대방 나감 안내)이면 보낸 사람이 없어 [senderId]가 null이다.
 * 도메인 로직은 [com.org.oneulsogae.core.chat.command.domain.ChatMessage]에 정의한다. (엔티티는 상태만 보관한다)
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

	/** 보낸 사람 id. SYSTEM 메세지는 보낸 사람이 없어 null. */
	@Column(name = "sender_id")
	val senderId: Long? = null,

	@Column(name = "content", nullable = false, length = 1000)
	val content: String,

	/** 메세지 유형. (일반 USER / 시스템 SYSTEM) */
	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, columnDefinition = "varchar(50)")
	val type: ChatMessageType = ChatMessageType.USER,

	/** 메세지를 보낸(또는 생성된) 시각. */
	@Column(name = "sent_at", nullable = false)
	val sentAt: LocalDateTime,
) : BaseEntity()
