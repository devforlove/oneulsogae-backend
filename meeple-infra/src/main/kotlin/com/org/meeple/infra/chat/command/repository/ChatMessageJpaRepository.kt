package com.org.meeple.infra.chat.command.repository

import com.org.meeple.infra.chat.command.entity.ChatMessageEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 채팅 메세지 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 저장 out-port는 [com.org.meeple.infra.chat.command.adapter.ChatMessageAdapter]가 구현한다.
 * 메세지 조회(키셋 페이지네이션)는 QueryDSL 구현체 [com.org.meeple.infra.chat.query.GetChatMessageDaoImpl]가 담당한다.
 */
interface ChatMessageJpaRepository : JpaRepository<ChatMessageEntity, Long>
