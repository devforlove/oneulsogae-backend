package com.org.meeple.infra.match.command.repository

import com.org.meeple.infra.match.command.entity.MatchedTeamEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 팀 매칭 참가 팀(matched_teams) 리포지토리.
 * [com.org.meeple.infra.match.command.adapter.TeamMatchAdapter]가 참가 팀 행 저장에 사용한다.
 */
interface MatchedTeamJpaRepository : JpaRepository<MatchedTeamEntity, Long>
