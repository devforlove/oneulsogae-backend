package com.org.meeple.infra.teammatch.query

import com.org.meeple.infra.teammatch.command.entity.RecommendedTeamHistoryEntity
import com.org.meeple.infra.teammatch.command.repository.RecommendedTeamHistoryJpaRepository
import com.org.meeple.scheduler.match.query.dao.GetRecommendedTeamHistoryDao
import org.springframework.stereotype.Component

/**
 * scheduler [GetRecommendedTeamHistoryDao]의 구현. user_id로 단건 seek해 매칭한 상대 team_id를 모은다.
 * (recommended_team_histories.ux_user_id_team_id의 선두 컬럼 user_id로 seek)
 */
@Component
class GetRecommendedTeamHistoryDaoImpl(
    private val recommendedTeamHistoryJpaRepository: RecommendedTeamHistoryJpaRepository,
) : GetRecommendedTeamHistoryDao {

    override fun findMatchedTeamIds(userId: Long): Set<Long> =
        recommendedTeamHistoryJpaRepository.findByUserId(userId)
            .map { entity: RecommendedTeamHistoryEntity -> entity.teamId }
            .toSet()
}
