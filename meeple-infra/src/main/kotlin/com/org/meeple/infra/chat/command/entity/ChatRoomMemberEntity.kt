package com.org.meeple.infra.chat.command.entity

import com.org.meeple.common.chat.ChatRoomMemberStatus
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * 채팅방에 참가한 사용자 한 명의 참가 상태 영속성 엔티티.
 * 기존 [ChatRoomEntity]는 안 읽은 개수를 성별로 비정규화해 들고 있으나, 이 엔티티는 (chat_room_id, user_id) 한 쌍을 한 행으로 두어 참가자별 읽음 상태를 정규화한다.
 * (chat_room_id, user_id) 유니크 제약으로 한 방에 같은 사용자가 중복 참가하는 것을 차단한다.
 * 유니크 제약이 chat_room_id 선두 조회(방의 참가자 목록)를 커버하고, user_id 단독 인덱스로 특정 사용자의 참가 방 조회를 커버한다.
 * 도메인 로직은 [com.org.meeple.core.chat.command.domain.ChatRoomMember] 모델에 정의한다. (엔티티는 상태만 보관한다)
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "chat_room_members",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_chat_room_id_user_id", columnNames = ["chat_room_id", "user_id"]),
	],
	indexes = [
		Index(name = "idx_user_id", columnList = "user_id"),
	],
)
class ChatRoomMemberEntity(
	@Column(name = "chat_room_id", nullable = false)
	val chatRoomId: Long,

	@Column(name = "user_id", nullable = false)
	val userId: Long,

	/** TEAM 매칭 방에서 이 참가자가 속한 팀 id. SOLO 방은 null. 같은 방 내 상대 팀 판별(내 행과 비교)에 쓴다. */
	@Column(name = "team_id")
	val teamId: Long? = null,

	/** 이 참가자의 활성 상태. (기본 활성) */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: ChatRoomMemberStatus = ChatRoomMemberStatus.ACTIVE,

	/** 이 참가자가 아직 확인하지 않은 메세지 개수. (상대가 보낸 메세지가 쌓이면 증가, 본인이 읽으면 0) */
	@Column(name = "unread_count", nullable = false)
	var unreadCount: Int = 0,

	/** 이 참가자가 마지막으로 메세지를 확인한 시각. (아직 읽은 적 없으면 null) */
	@Column(name = "last_read_at")
	var lastReadAt: LocalDateTime? = null,

	/** 이 참가자가 마지막으로 읽은 메세지 id. (한 번도 안 읽었으면 null) 말풍선별 안 읽은 사람 수 계산의 읽음 포인터다. */
	@Column(name = "last_read_message_id")
	var lastReadMessageId: Long? = null,

	/** 이 참가자가 채팅방에 참가한 시각. */
	@Column(name = "joined_at", nullable = false)
	val joinedAt: LocalDateTime,

	/** 이 참가자가 채팅방을 나간 시각. null이면 아직 참가 중이다. */
	@Column(name = "exited_at")
	var exitedAt: LocalDateTime? = null,
) : BaseEntity()
