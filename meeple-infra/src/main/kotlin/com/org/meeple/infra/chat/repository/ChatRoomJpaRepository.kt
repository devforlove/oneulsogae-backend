package com.org.meeple.infra.chat.repository

import com.org.meeple.infra.chat.entity.ChatRoomEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 채팅방 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 저장은 [com.org.meeple.infra.chat.adapter.ChatRoomCoreAdapter](SaveChatRoomPort)가 이 리포지토리로 처리한다.
 * QueryDSL 조회는 [com.org.meeple.infra.chat.adapter.ChatRoomQueryCoreAdapter](GetChatRoomPort)가 직접 수행한다.
 */
interface ChatRoomJpaRepository : JpaRepository<ChatRoomEntity, Long>
