package com.org.meeple.infra.match.command.repository

import com.org.meeple.infra.match.command.entity.TeamEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 팀 헤더 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * [com.org.meeple.infra.match.command.adapter.TeamAdapter]가 팀 애그리거트(헤더+구성원) 영속화에서 사용한다.
 */
interface TeamJpaRepository : JpaRepository<TeamEntity, Long>
