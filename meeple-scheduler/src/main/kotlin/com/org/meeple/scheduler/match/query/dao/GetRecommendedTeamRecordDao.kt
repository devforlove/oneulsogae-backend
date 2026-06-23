package com.org.meeple.scheduler.match.query.dao

import java.time.LocalDate

/**
 * 팀 추천 적재 이력 조회 dao. (조회 전용 — 적재는 SaveRecommendedTeamPort가 담당) QueryDSL 구현은 infra가 담당한다.
 * "하루에 한 번" 멱등을 위해, 주어진 일자에 이미 추천된 유저를 신규 추천에서 제외하는 데 쓴다.
 */
interface GetRecommendedTeamRecordDao {

    /** recommended_teams.recommended_date = [date]인 user_id 집합. */
    fun findUserIdsRecommendedOn(date: LocalDate): Set<Long>
}
