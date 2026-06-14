package com.org.meeple.infra.chat.repository

import com.org.meeple.infra.chat.entity.ChatRoomMemberEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 채팅방 참가자 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 저장(기본 CRUD)과 단순 존재 조회(파생 쿼리)를 [com.org.meeple.infra.chat.adapter.ChatRoomMemberCoreAdapter]가 이 리포지토리로 처리한다.
 * 프로필 조인이 필요한 조회는 별도 Query 어댑터([com.org.meeple.infra.chat.adapter.ChatRoomMemberQueryCoreAdapter])로 분리돼 있다.
 */
interface ChatRoomMemberJpaRepository : JpaRepository<ChatRoomMemberEntity, Long> {

	fun existsByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): Boolean
}
