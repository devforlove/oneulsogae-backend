package com.org.meeple.infra.match.query

import com.org.meeple.scheduler.match.query.dao.GetUserMatchHistoryDao
import com.org.meeple.scheduler.match.query.dto.PreviouslyMatchedTeams
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component

/**
 * scheduler [GetUserMatchHistoryDao]의 구현. 유저가 과거 소속했던 팀이 MATCHED된 적 있는 상대 team_id를 조회한다.
 *
 * team_members·matched_teams·team_matches가 모두 @SQLRestriction("deleted_at is null")로 소프트 삭제를
 * 감추므로, 종료된 과거 이력을 읽으려면 네이티브 SQL로 우회한다. (QueryDSL/JPQL로는 @SQLRestriction 우회 불가)
 *
 * 성사 판정: 성사 시 expires_at을 +100년 연장(core MATCHED_EXPIRATION_EXTENSION_YEARS)하고 종료해도
 * 되돌리지 않으므로, 'expires_at > introduced_date + 50년'이면 그 매칭은 한 번 MATCHED된 것이다.
 * (정상 만료는 소개일+며칠 수준 → 50년이 안전한 분리점이며, 매칭의 자기 introduced_date 기준이라 실행 시점과 무관하게 정확)
 *
 * 인덱스: team_members.idx_user_id(IN seek) → matched_teams.idx_team_id → team_matches PK →
 * matched_teams.ux_team_match_id_team_id. 풀스캔/filesort 없음.
 */
@Component
class GetUserMatchHistoryDaoImpl(
    private val entityManager: EntityManager,
) : GetUserMatchHistoryDao {

    override fun findPreviouslyMatchedTeamIdsByUser(userIds: Set<Long>): PreviouslyMatchedTeams {
        if (userIds.isEmpty()) return PreviouslyMatchedTeams(emptyMap())
        val sql: String = """
            SELECT tmself.user_id, mt_opp.team_id
            FROM team_members tmself
            JOIN matched_teams mt_self ON mt_self.team_id = tmself.team_id
            JOIN team_matches t        ON t.id = mt_self.team_match_id
            JOIN matched_teams mt_opp  ON mt_opp.team_match_id = t.id AND mt_opp.team_id <> tmself.team_id
            WHERE tmself.user_id IN (:userIds)
              AND t.expires_at > t.introduced_date + INTERVAL 50 YEAR
        """.trimIndent()
        @Suppress("UNCHECKED_CAST")
        val rows: List<Array<Any>> = entityManager
            .createNativeQuery(sql)
            .setParameter("userIds", userIds)
            .resultList as List<Array<Any>>
        val opponentTeamIdsByUser: Map<Long, Set<Long>> = rows
            .groupBy({ row: Array<Any> -> (row[0] as Number).toLong() }, { row: Array<Any> -> (row[1] as Number).toLong() })
            .mapValues { (_: Long, teamIds: List<Long>) -> teamIds.toSet() }
        return PreviouslyMatchedTeams(opponentTeamIdsByUser)
    }
}
