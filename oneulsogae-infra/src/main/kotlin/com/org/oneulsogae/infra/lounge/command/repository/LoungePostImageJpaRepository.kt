package com.org.oneulsogae.infra.lounge.command.repository

import com.org.oneulsogae.infra.lounge.command.entity.LoungePostImageEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 라운지 글 사진 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.oneulsogae.infra.lounge.command.adapter.LoungePostImageAdapter]가 구현한다.
 */
interface LoungePostImageJpaRepository : JpaRepository<LoungePostImageEntity, Long>
