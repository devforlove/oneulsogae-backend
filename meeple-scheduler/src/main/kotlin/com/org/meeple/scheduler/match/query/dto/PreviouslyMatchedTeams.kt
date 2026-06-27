package com.org.meeple.scheduler.match.query.dto

/**
 * 유저별로, 그 유저가 과거(소속했던 팀 기준) MATCHED됐던 상대 team_id 집합을 보관하는 읽기 모델.
 * 추천 배치가 후보 팀에서 "이미 매칭했던 상대"를 유저 단위로 제외하는 데 쓴다.
 */
class PreviouslyMatchedTeams(
    private val opponentTeamIdsByUser: Map<Long, Set<Long>>,
) {
    /** [userId]가 과거 MATCHED됐던 상대 team_id. 이력이 없으면 빈 집합. */
    fun opponentTeamIdsOf(userId: Long): Set<Long> =
        opponentTeamIdsByUser[userId] ?: emptySet()
}
