package com.org.meeple.scheduler.teammatch.query.dao

/**
 * 유저가 과거 MATCHED한 상대 팀 조회 dao. (조회 전용) 구현은 infra가 담당한다.
 * 배치가 유저별 단건 seek로 재매칭 상대를 제외하는 데 쓴다.
 */
interface GetRecommendedTeamHistoryDao {

    /** [userId]가 과거 매칭한 상대 team_id 집합. 없으면 빈 집합. */
    fun findMatchedTeamIds(userId: Long): Set<Long>
}
