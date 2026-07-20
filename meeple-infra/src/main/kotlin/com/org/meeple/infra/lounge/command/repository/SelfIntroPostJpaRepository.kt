package com.org.meeple.infra.lounge.command.repository

import com.org.meeple.infra.lounge.command.entity.SelfIntroPostEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 셀소 본문 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.meeple.infra.lounge.command.adapter.SelfIntroPostAdapter]가 구현한다.
 */
interface SelfIntroPostJpaRepository : JpaRepository<SelfIntroPostEntity, Long>
