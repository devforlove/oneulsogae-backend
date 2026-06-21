package com.org.meeple.infra.chat.command.entity

import com.org.meeple.common.chat.ChatRoomStatus
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * 채팅방의 방 공통 상태 영속성 엔티티.
 * 누가 참가했는지(참가자)는 방이 아니라 [ChatRoomMemberEntity]가 (chat_room_id, user_id) 행으로 보관하는 단일 진실원천이므로, 방은 참가자 식별 컬럼을 들지 않는다.
 * (1:1·그룹챗 모두 같은 테이블을 쓰며, 사용자별 채팅방 목록 조회도 그 테이블(user_id)을 기점으로 한다)
 * expired_at 이후로는 만료된 채팅방으로 본다.
 * 도메인 로직은 chat 도메인 모델에 정의한다. (엔티티는 상태만 보관한다)
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "chat_rooms",
	// 매칭당 채팅방은 1개. 동시/재시도로 인한 중복 생성을 DB에서 막는다. (멱등 생성의 최종 가드)
	uniqueConstraints = [UniqueConstraint(name = "ux_match_id", columnNames = ["match_id"])],
)
class ChatRoomEntity(
	/** 이 채팅방을 생성시킨 매칭 id. */
	@Column(name = "match_id", nullable = false)
	val matchId: Long,

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	var status: ChatRoomStatus = ChatRoomStatus.ACTIVE,

	/** 채팅방 만료 시각. 이 시각 이후로는 만료된 채팅방으로 본다. */
	@Column(name = "expired_at", nullable = false)
	val expiredAt: LocalDateTime,

	/** 마지막으로 주고받은 메세지 내용. (아직 메세지가 없으면 null) */
	@Column(name = "last_message", length = 1000)
	var lastMessage: String? = null,

	/** 마지막 메세지 수신 시각. (아직 메세지가 없으면 null) */
	@Column(name = "last_message_at")
	var lastMessageAt: LocalDateTime? = null,
) : BaseEntity()
