package com.org.meeple.infra.teammatch.command.repository

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.infra.teammatch.command.entity.TeamMatchEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 팀 매칭 헤더(team_matches) 리포지토리.
 * [com.org.meeple.infra.teammatch.command.adapter.TeamMatchAdapter]가 헤더 저장·조회에 사용한다.
 */
interface TeamMatchJpaRepository : JpaRepository<TeamMatchEntity, Long> {

	/** 주어진 id들 중 status가 [status]가 아닌 헤더들. (종료되지 않은 진행 중 매칭 선별, PK IN seek) */
	fun findByIdInAndStatusNot(ids: List<Long>, status: MatchStatus): List<TeamMatchEntity>
}
