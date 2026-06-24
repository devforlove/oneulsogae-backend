package com.org.meeple.infra.match.command.repository

import com.org.meeple.infra.match.command.entity.TeamMatchEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 팀 매칭 헤더(team_matches) 리포지토리.
 * [com.org.meeple.infra.match.command.adapter.TeamMatchAdapter]가 헤더 저장에 사용한다.
 */
interface TeamMatchJpaRepository : JpaRepository<TeamMatchEntity, Long>
