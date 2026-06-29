package com.org.meeple.infra.teammatch.query

import com.org.meeple.infra.teammatch.command.entity.QRecommendedTeamEntity
import com.org.meeple.scheduler.teammatch.query.dao.GetRecommendedTeamRecordDao
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * scheduler [GetRecommendedTeamRecordDao]의 QueryDSL 구현. (조회 전용)
 * recommended_teams에서 recommended_date 동등 조건으로 user_id를 모은다. (유저당 1행이라 fan-out 없음)
 */
@Component
class GetRecommendedTeamRecordDaoImpl(
    private val queryFactory: JPAQueryFactory,
) : GetRecommendedTeamRecordDao {

    override fun findUserIdsRecommendedOn(date: LocalDate): Set<Long> {
        val recommended: QRecommendedTeamEntity = QRecommendedTeamEntity.recommendedTeamEntity
        return queryFactory
            .select(recommended.userId)
            .from(recommended)
            .where(recommended.recommendedDate.eq(date))
            .fetch()
            .toSet()
    }
}
