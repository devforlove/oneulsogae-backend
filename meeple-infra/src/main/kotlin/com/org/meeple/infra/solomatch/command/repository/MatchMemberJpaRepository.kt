package com.org.meeple.infra.solomatch.command.repository

import com.org.meeple.infra.solomatch.command.entity.SoloMatchMemberEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 매칭 참가자 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 참가자 저장·매칭별 참가자 조회(파생 쿼리)를 담당한다.
 * [com.org.meeple.infra.solomatch.command.adapter.MatchAdapter]가 매칭 애그리거트(헤더+참가자) 영속화에서 사용한다.
 * 참가자↔매칭 조인이 필요한 조회(성사 사용자 등)는 [com.org.meeple.infra.solomatch.query]의 QueryDSL dao가 담당한다.
 */
interface MatchMemberJpaRepository : JpaRepository<SoloMatchMemberEntity, Long> {

	/** 한 매칭의 참가자 전체. (매칭 애그리거트 조립용) */
	fun findByMatchId(matchId: Long): List<SoloMatchMemberEntity>
}
