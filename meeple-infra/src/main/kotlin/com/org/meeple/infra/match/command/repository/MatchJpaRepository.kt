package com.org.meeple.infra.match.command.repository

import com.org.meeple.infra.match.command.entity.MatchEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 매칭 헤더 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * [com.org.meeple.infra.match.command.adapter.MatchAdapter]가 이 리포지토리로 헤더 저장·단건 조회·재소개 존재 확인을 구현한다.
 * 참가자(match_members) 조인이 필요한 조회(일일 소개 존재·성사 사용자)는 [MatchMemberJpaRepository]가 담당한다.
 */
interface MatchJpaRepository : JpaRepository<MatchEntity, Long> {

	/** 참가자 조합 키로 소개 이력 존재 여부. (udx_member_key 사용, 재소개 방지) */
	fun existsByMemberKey(memberKey: String): Boolean
}
