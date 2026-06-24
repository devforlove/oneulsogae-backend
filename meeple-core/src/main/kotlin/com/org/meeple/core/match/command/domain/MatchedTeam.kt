package com.org.meeple.core.match.command.domain

import java.time.LocalDateTime

/**
 * 팀 매칭([TeamMatch])에 참가한 한 팀을 (teamMatchId, teamId) 한 쌍으로 나타내는 도메인 모델.
 * 매치별 수락 여부([accepted])를 팀이 아니라 이 모델이 보관한다. (응답 전이면 null)
 */
data class MatchedTeam(
	val id: Long = 0,
	val teamMatchId: Long,
	val teamId: Long,
	val accepted: Boolean? = null,
	val deletedAt: LocalDateTime? = null,
)
