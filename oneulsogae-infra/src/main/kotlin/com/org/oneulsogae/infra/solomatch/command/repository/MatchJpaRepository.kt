package com.org.oneulsogae.infra.solomatch.command.repository

import com.org.oneulsogae.infra.solomatch.command.entity.SoloMatchEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 매칭 헤더 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * [com.org.oneulsogae.infra.solomatch.command.adapter.MatchAdapter]가 이 리포지토리로 헤더 저장·단건 조회를 구현한다.
 * 재소개 존재 확인·성사 사용자 등 참가자(solo_match_members) 조인이 필요한 조회는 [com.org.oneulsogae.infra.solomatch.query]의 QueryDSL dao가 담당한다.
 */
interface MatchJpaRepository : JpaRepository<SoloMatchEntity, Long>
