package com.org.meeple.infra.match.query

import com.org.meeple.common.match.TeamStatus
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.scheduler.match.query.dao.GetCandidateTeamDao
import com.org.meeple.scheduler.match.query.dto.CandidateTeam
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * scheduler [GetCandidateTeamDao]의 QueryDSL 구현. (조회 전용)
 * teams(@SQLRestriction으로 소프트삭제 제외)에서 status=ACTIVE인 팀의 id·성별·활동권역(region_id)을 전부 반환한다.
 * 권역은 teams.region_id를 직접 쓴다. (팀당 1행 — 조인/fan-out 없음)
 */
@Component
class GetCandidateTeamDaoImpl(
    private val queryFactory: JPAQueryFactory,
) : GetCandidateTeamDao {

    override fun findCandidateTeams(): List<CandidateTeam> {
        val team: QTeamEntity = QTeamEntity.teamEntity
        return queryFactory
            .select(
                Projections.constructor(
                    CandidateTeam::class.java,
                    team.id,
                    team.gender,
                    team.regionId,
                ),
            )
            .from(team)
            .where(team.status.eq(TeamStatus.ACTIVE))
            .fetch()
    }
}
