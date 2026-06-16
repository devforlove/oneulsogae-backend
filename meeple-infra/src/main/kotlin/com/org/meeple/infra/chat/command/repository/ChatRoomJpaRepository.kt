package com.org.meeple.infra.chat.command.repository

import com.org.meeple.infra.chat.command.entity.ChatRoomEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * 채팅방 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 저장·단건 로드는 [com.org.meeple.infra.chat.command.adapter.ChatRoomAdapter](core SaveChatRoomPort/GetChatRoomPort)가 처리한다.
 * 발송 경로(chatting)는 방을 로드하지 않고 조건부 UPDATE([updateLastMessageIfActive])로 종료검증+마지막 메세지 갱신을 한 번에 한다.
 */
interface ChatRoomJpaRepository : JpaRepository<ChatRoomEntity, Long> {

	/** 매칭 id로 채팅방을 단건 조회한다. 없으면 null. (match_id 유니크) */
	fun findByMatchId(matchId: Long): ChatRoomEntity?

	/**
	 * 활성(ACTIVE) 상태인 방에 한해 마지막 메세지/수신 시각을 갱신한다. 갱신된 행 수를 반환한다. (0이면 없음/종료됨)
	 * 벌크 JPQL UPDATE는 @SQLRestriction(소프트삭제 필터)이 자동 적용되지 않으므로 `deleted_at` 조건을 명시한다.
	 */
	@Modifying
	@Query(
		"""
		update ChatRoomEntity r
		set r.lastMessage = :lastMessage, r.lastMessageAt = :lastMessageAt
		where r.id = :chatRoomId
		  and r.status = com.org.meeple.common.chat.ChatRoomStatus.ACTIVE
		  and r.deletedAt is null
		""",
	)
	fun updateLastMessageIfActive(
		@Param("chatRoomId") chatRoomId: Long,
		@Param("lastMessage") lastMessage: String,
		@Param("lastMessageAt") lastMessageAt: LocalDateTime,
	): Int
}
