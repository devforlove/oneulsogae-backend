package com.org.oneulsogae.infra.teammatch.command.repository

import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.infra.teammatch.command.entity.TeamMatchEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 팀 매칭 헤더(team_matches) 리포지토리.
 * [com.org.oneulsogae.infra.teammatch.command.adapter.TeamMatchAdapter]가 헤더 저장·조회에 사용한다.
 */
interface TeamMatchJpaRepository : JpaRepository<TeamMatchEntity, Long> {

	/** 주어진 id들 중 status가 [status]가 아닌 헤더들. (종료되지 않은 진행 중 매칭 선별, PK IN seek) */
	fun findByIdInAndStatusNot(ids: List<Long>, status: MatchStatus): List<TeamMatchEntity>

	/**
	 * [memberKey] 조합의 팀 매칭이 **소프트삭제 행 포함** 존재하는지 카운트한다. (ux_member_key seek, 0 또는 1)
	 * ux_member_key 유니크는 deleted_at과 무관하게 컬럼에 걸려 있어, 과거 소개돼 종료·소프트삭제된 조합의 재소개도 막는다(재소개 방지).
	 * 따라서 승격 전 존재 검사는 @SQLRestriction(deleted_at is null)을 우회하는 네이티브 쿼리로 소프트삭제까지 봐야 유니크 위반(5xx)을 막을 수 있다.
	 */
	@Query(value = "select count(1) from team_matches where member_key = :memberKey", nativeQuery = true)
	fun countByMemberKeyIncludingDeleted(@Param("memberKey") memberKey: String): Long
}
