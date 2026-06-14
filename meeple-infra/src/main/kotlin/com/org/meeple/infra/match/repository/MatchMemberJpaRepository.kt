package com.org.meeple.infra.match.repository

import com.org.meeple.infra.match.entity.MatchMemberEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 매칭 참가자 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 현재는 매칭 생성 시 참가자 저장(기본 CRUD)만 [com.org.meeple.infra.match.adapter.MatchMemberCoreAdapter]가 사용한다.
 * (조회가 필요해지면 파생 쿼리/별도 Query 어댑터로 확장한다)
 */
interface MatchMemberJpaRepository : JpaRepository<MatchMemberEntity, Long>
