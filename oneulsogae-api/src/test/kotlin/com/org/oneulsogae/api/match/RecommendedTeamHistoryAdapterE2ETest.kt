package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.core.teammatch.command.application.port.out.SaveRecommendedTeamHistoryPort
import com.org.oneulsogae.core.teammatch.command.domain.RecommendedTeamHistory
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.teammatch.command.entity.QRecommendedTeamHistoryEntity
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.springframework.beans.factory.annotation.Autowired

/**
 * 매칭 성사 이력 저장 어댑터(멱등) 실DB 검증.
 * saveAll로 (유저 → 상대 팀)을 저장하고, 재호출 시 이미 있는 (user_id, team_id)는 중복 저장하지 않는다.
 */
class RecommendedTeamHistoryAdapterE2ETest : AbstractIntegrationSupport() {

    @Autowired
    private lateinit var saveRecommendedTeamHistoryPort: SaveRecommendedTeamHistoryPort

    init {
        describe("saveAll") {
            it("이력을 저장하고, 재호출 시 중복 (user_id, team_id)는 건너뛴다") {
                saveRecommendedTeamHistoryPort.saveAll(
                    listOf(
                        RecommendedTeamHistory(userId = 1L, teamId = 10L),
                        RecommendedTeamHistory(userId = 2L, teamId = 10L),
                    ),
                )
                // 재호출: (1,10) 중복 + (1,20) 신규
                saveRecommendedTeamHistoryPort.saveAll(
                    listOf(
                        RecommendedTeamHistory(userId = 1L, teamId = 10L),
                        RecommendedTeamHistory(userId = 1L, teamId = 20L),
                    ),
                )

                teamIdsOf(1L) shouldContainExactlyInAnyOrder listOf(10L, 20L)
                teamIdsOf(2L) shouldContainExactlyInAnyOrder listOf(10L)
            }
        }

        afterTest {
            IntegrationUtil.deleteAll(QRecommendedTeamHistoryEntity.recommendedTeamHistoryEntity)
        }
    }
}

private fun teamIdsOf(userId: Long): List<Long> {
    val q = QRecommendedTeamHistoryEntity.recommendedTeamHistoryEntity
    return IntegrationUtil.getQuery().select(q.teamId).from(q).where(q.userId.eq(userId)).fetch()
}
