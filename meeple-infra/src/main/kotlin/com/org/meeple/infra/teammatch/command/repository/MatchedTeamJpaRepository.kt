package com.org.meeple.infra.teammatch.command.repository

import com.org.meeple.infra.teammatch.command.entity.MatchedTeamEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 팀 매칭 참가 팀(matched_teams) 리포지토리.
 * [com.org.meeple.infra.teammatch.command.adapter.TeamMatchAdapter]가 참가 팀 행 저장·조회에 사용한다.
 */
interface MatchedTeamJpaRepository : JpaRepository<MatchedTeamEntity, Long> {

	/** 팀별 참가 행. (idx_team_id seek) */
	fun findByTeamId(teamId: Long): List<MatchedTeamEntity>

	/** 매칭들의 참가 팀 행 전부. (ux_team_match_id_team_id 선두 team_match_id seek) */
	fun findByTeamMatchIdIn(teamMatchIds: List<Long>): List<MatchedTeamEntity>
}
