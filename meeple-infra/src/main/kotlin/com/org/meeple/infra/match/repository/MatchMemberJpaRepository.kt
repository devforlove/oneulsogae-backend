package com.org.meeple.infra.match.repository

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.infra.match.entity.MatchMemberEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 매칭 참가자 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 참가자 저장·매칭별 참가자 조회(파생 쿼리)와, 참가자↔매칭 조인이 필요한 조회(일일 소개 존재·성사 사용자, JPQL)를 담당한다.
 * [com.org.meeple.infra.match.adapter.MatchCoreAdapter]가 매칭 애그리거트(헤더+참가자) 영속화에서 사용한다.
 */
interface MatchMemberJpaRepository : JpaRepository<MatchMemberEntity, Long> {

	/** 한 매칭의 참가자 전체. (매칭 애그리거트 조립용) */
	fun findByMatchId(matchId: Long): List<MatchMemberEntity>

	/**
	 * [status] 상태인 매칭에 속한 사용자 ID 전체. (배치의 매칭 사용자 제외 집합 산출용)
	 * 참가자 행에서 출발해 매칭 헤더와 명시적 조인하고, 상태만 where로 거른다. (중복 정리는 호출 측 Set이 맡는다)
	 */
	@Query(
		"""
		select m.userId
		from MatchMemberEntity m
		join MatchEntity mt on mt.id = m.matchId
		where mt.status = :status
		""",
	)
	fun findUserIdsByMatchStatus(@Param("status") status: MatchStatus): List<Long>
}
