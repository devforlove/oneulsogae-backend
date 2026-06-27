package com.org.meeple.scheduler.match.query.dao

import com.org.meeple.scheduler.match.query.dto.PreviouslyMatchedTeams

/**
 * 유저의 과거 MATCHED 상대 팀 조회 dao. (조회 전용) 구현은 infra가 담당한다.
 *
 * 솔로 유저는 과거 소속 팀(team_members)·매칭(matched_teams·team_matches)이 모두 소프트 삭제 상태일 수 있으므로,
 * 구현은 deleted_at 필터 없이 조회한다. 성사 여부는 team_matches.expires_at 연장 흔적으로 판정한다.
 */
interface GetUserMatchHistoryDao {

    /**
     * 주어진 [userIds]가 과거에 소속했던 팀 기준으로 MATCHED된 적 있는 상대 team_id를 유저별로 묶어 반환한다.
     * [userIds]가 비면 빈 결과를 반환한다.
     */
    fun findPreviouslyMatchedTeamIdsByUser(userIds: Set<Long>): PreviouslyMatchedTeams
}
