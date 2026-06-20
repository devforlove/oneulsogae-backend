package com.org.meeple.infra.match.command.repository

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.infra.match.command.entity.SoloMatchEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * 매칭 헤더 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * [com.org.meeple.infra.match.command.adapter.MatchAdapter]가 이 리포지토리로 헤더 저장·단건 조회를 구현한다.
 * 재소개 존재 확인·성사 사용자 등 참가자(solo_match_members) 조인이 필요한 조회는 [com.org.meeple.infra.match.query]의 QueryDSL dao가 담당한다.
 */
interface MatchJpaRepository : JpaRepository<SoloMatchEntity, Long> {

	/**
	 * 만료된 소개의 id를 조회한다.
	 * 아직 응답 대기 상태([statuses]: PROPOSED/PARTIALLY_ACCEPTED)이면서 만료 시각([expiresAt])이 [now] 이전인 매칭만 가져온다.
	 * (성사(MATCHED)는 만료가 100년 뒤로 미뤄져 대상이 아니고, 종료(CLOSED)는 soft delete되어 @SQLRestriction으로 제외된다)
	 */
	@Query(
		"""
		select m.id
		from SoloMatchEntity m
		where m.status in :statuses
		  and m.expiresAt < :now
		""",
	)
	fun findExpiredMatchIds(
		@Param("now") now: LocalDateTime,
		@Param("statuses") statuses: Collection<MatchStatus>,
	): List<Long>
}
