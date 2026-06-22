package com.org.meeple.core.match.query.service.port.`in`

import com.org.meeple.core.match.query.dto.RecommendedTeam

/**
 * 팀 없는 솔로 유저에게 추천된 팀을 조회하는 유스케이스(인포트). 추천이 없으면 null.
 */
interface GetRecommendedTeamUseCase {

	fun get(userId: Long): RecommendedTeam?
}
