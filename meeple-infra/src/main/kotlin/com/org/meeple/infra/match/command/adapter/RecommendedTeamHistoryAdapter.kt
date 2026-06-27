package com.org.meeple.infra.match.command.adapter

import com.org.meeple.core.match.command.application.port.out.SaveRecommendedTeamHistoryPort
import com.org.meeple.core.match.command.domain.RecommendedTeamHistory
import com.org.meeple.infra.match.command.entity.RecommendedTeamHistoryEntity
import com.org.meeple.infra.match.command.repository.RecommendedTeamHistoryJpaRepository
import org.springframework.stereotype.Component

/**
 * [RecommendedTeamHistoryEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 성사 이력 저장 out-port를 구현한다. 이미 있는 (user_id, team_id)는 건너뛰어 멱등을 보장한다.
 * (같은 유저가 같은 상대와 재매칭해도 유니크 위반으로 성사 트랜잭션이 롤백되지 않게 한다)
 */
@Component
class RecommendedTeamHistoryAdapter(
    private val recommendedTeamHistoryJpaRepository: RecommendedTeamHistoryJpaRepository,
) : SaveRecommendedTeamHistoryPort {

    override fun saveAll(histories: List<RecommendedTeamHistory>) {
        histories.forEach { history: RecommendedTeamHistory ->
            if (!recommendedTeamHistoryJpaRepository.existsByUserIdAndTeamId(history.userId, history.teamId)) {
                recommendedTeamHistoryJpaRepository.save(
                    RecommendedTeamHistoryEntity(userId = history.userId, teamId = history.teamId),
                )
            }
        }
    }
}
