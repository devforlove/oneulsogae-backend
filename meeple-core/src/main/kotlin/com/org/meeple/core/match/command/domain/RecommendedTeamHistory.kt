package com.org.meeple.core.match.command.domain

/**
 * 매칭 성사 시 남기는 (유저 → 매칭한 상대 팀) 이력 한 건.
 * 추천 배치가 "이미 매칭한 상대 팀"을 그 유저에게 다시 추천하지 않도록 거르는 데 쓴다.
 */
data class RecommendedTeamHistory(
	val userId: Long,
	val teamId: Long,
)
