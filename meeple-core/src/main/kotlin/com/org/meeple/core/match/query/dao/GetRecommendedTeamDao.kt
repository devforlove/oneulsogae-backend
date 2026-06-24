package com.org.meeple.core.match.query.dao

import com.org.meeple.core.match.query.dto.RecommendedTeam

/**
 * 솔로 유저에게 추천된 팀 조회 dao(query out-port). QueryDSL 구현은 infra가 담당한다.
 * 추천 행이 가리키는 ACTIVE 팀들을 팀원 프로필과 함께 최신순으로 반환한다. 추천이 없거나 팀이 모두 해체됐으면 빈 리스트.
 */
interface GetRecommendedTeamDao {

	fun findByUserId(userId: Long): List<RecommendedTeam>
}
