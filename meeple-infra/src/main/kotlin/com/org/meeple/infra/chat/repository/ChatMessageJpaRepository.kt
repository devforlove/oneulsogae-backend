package com.org.meeple.infra.chat.repository

import com.org.meeple.infra.chat.entity.ChatMessageEntity
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 채팅 메세지 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.meeple.infra.chat.adapter.ChatMessageCoreAdapter]가 구현한다.
 * 조인 없이 (chat_room_id, id) 인덱스만 타는 키셋 페이지네이션이라 파생 쿼리(메서드 쿼리)로 충분하다.
 */
interface ChatMessageJpaRepository : JpaRepository<ChatMessageEntity, Long> {

	/** 특정 방의 최신 메세지부터 [limit]건. (idx_chat_room_id_id 역방향 스캔, 첫 페이지) */
	fun findByChatRoomIdOrderByIdDesc(chatRoomId: Long, limit: Limit): List<ChatMessageEntity>

	/** 특정 방에서 [id]보다 과거(더 작은 id)인 메세지를 최신순 [limit]건. (키셋 다음 페이지) */
	fun findByChatRoomIdAndIdLessThanOrderByIdDesc(chatRoomId: Long, id: Long, limit: Limit): List<ChatMessageEntity>
}
