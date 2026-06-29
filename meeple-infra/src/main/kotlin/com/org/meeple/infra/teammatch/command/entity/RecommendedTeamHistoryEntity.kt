package com.org.meeple.infra.teammatch.command.entity

import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 유저가 과거 매칭(MATCHED)한 상대 팀 이력. 추천 배치가 재매칭 상대를 제외하는 데 쓴다.
 * 성사 시점에 append-only로 기록한다(소프트 삭제 없음). UNIQUE(user_id, team_id)로 멱등 + 조회 seek.
 */
@Entity
@Table(
    name = "recommended_team_histories",
    uniqueConstraints = [
        UniqueConstraint(name = "ux_user_id_team_id", columnNames = ["user_id", "team_id"]),
    ],
)
class RecommendedTeamHistoryEntity(
    /** 매칭한 유저. */
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    /** 그 유저가 매칭한 상대 팀. */
    @Column(name = "team_id", nullable = false, updatable = false)
    val teamId: Long,
) : BaseEntity()
