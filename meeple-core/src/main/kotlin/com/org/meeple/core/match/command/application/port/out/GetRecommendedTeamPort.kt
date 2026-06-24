package com.org.meeple.core.match.command.application.port.out

/**
 * 추천 팀(recommended_teams) 단건 조회 포트. (명령 트랜잭션 내 보조 조회 — 잠금/상태변경 없음)
 * 팀 결성 시 각 구성원에게 추천됐던 팀 id를 읽어 팀 매칭으로 승격하는 데 쓴다.
 */
interface GetRecommendedTeamPort {

	/** [userId]에게 추천된 팀 id. 추천이 없으면 null. */
	fun findRecommendedTeamId(userId: Long): Long?
}
