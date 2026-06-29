package com.org.meeple.scheduler.teammatch.query.dao

import com.org.meeple.scheduler.teammatch.query.dto.RecommendableSoloUser
import java.time.LocalDateTime

/**
 * 팀 추천 대상(팀 미소속 솔로 유저) 조회 dao. QueryDSL 구현은 infra가 담당한다.
 * match_user에 있으나 비삭제 team_members 행이 전혀 없고 [loginAfter] 이후 로그인한 유저를 전부 반환한다. (솔로 배치와 동일하게 전체 1회 적재)
 */
interface GetRecommendableSoloUserDao {

    fun findRecommendableSoloUsers(loginAfter: LocalDateTime): List<RecommendableSoloUser>
}
