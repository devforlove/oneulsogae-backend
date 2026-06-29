package com.org.meeple.core.teammatch.command.domain.event

/**
 * 성사된 팀 매칭을 한 팀이 종료(나감)했을 때 발행되는 도메인 이벤트.
 * [recipientUserIds]는 방에 남는 상대 팀의 활성 구성원, [fromTeamId]는 나간 팀(수신자 기준 상대 팀)이다.
 */
data class TeamMatchEnded(
	val teamMatchId: Long,
	val fromTeamId: Long,
	val recipientUserIds: List<Long>,
)
