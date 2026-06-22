package com.org.meeple.scheduler.match.query.dao

import com.org.meeple.scheduler.match.query.dto.RecommendableSoloUser

/**
 * 팀 추천 대상(팀 미소속 솔로 유저) 조회 dao. QueryDSL 구현은 infra가 담당한다.
 * match_user에 있으나 비삭제 team_members 행이 전혀 없는 유저를 user_id 키셋으로 페이징해 반환한다.
 */
interface GetRecommendableSoloUserDao {

	/** [cursor](직전 페이지 마지막 user_id, 첫 페이지 null) 이후 user_id 오름차순으로 [limit]개를 반환한다. */
	fun findTargets(cursor: Long?, limit: Int): List<RecommendableSoloUser>
}
