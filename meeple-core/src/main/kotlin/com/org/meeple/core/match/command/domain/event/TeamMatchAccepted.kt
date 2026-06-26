package com.org.meeple.core.match.command.domain.event

/**
 * 팀 매칭이 양 팀 신청으로 성사(MATCHED)됐을 때 발행되는 도메인 이벤트.
 * 알림은 양 팀에 각각 발행된다 — [recipientUserIds]는 한 팀의 수신자(마지막에 신청해 성사를 만든 본인을 제외한 구성원),
 * [fromTeamId]는 그 수신자들의 상대 팀이다. (수신자 기준 "상대 팀"을 보여주기 위해 팀마다 상대 팀 id를 담는다)
 */
data class TeamMatchAccepted(
	val teamMatchId: Long,
	val fromTeamId: Long,
	val recipientUserIds: List<Long>,
)
