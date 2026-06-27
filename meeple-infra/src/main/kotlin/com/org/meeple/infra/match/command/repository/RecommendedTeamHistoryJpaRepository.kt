package com.org.meeple.infra.match.command.repository

import com.org.meeple.infra.match.command.entity.RecommendedTeamHistoryEntity
import org.springframework.data.jpa.repository.JpaRepository

interface RecommendedTeamHistoryJpaRepository : JpaRepository<RecommendedTeamHistoryEntity, Long> {

    /** 멱등 저장용: 이미 같은 (user_id, team_id)가 있는지. */
    fun existsByUserIdAndTeamId(userId: Long, teamId: Long): Boolean

    /** 조회용: 유저가 매칭한 상대 팀 행들. */
    fun findByUserId(userId: Long): List<RecommendedTeamHistoryEntity>
}
