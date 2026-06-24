package com.org.meeple.core.match.command.domain.event

/**
 * 팀 매칭이 양 팀 신청으로 성사(MATCHED)됐을 때 발행되는 도메인 이벤트.
 * [recipientUserIds]는 알림 수신자(양 팀 4인 중 마지막에 신청해 성사를 만든 본인을 제외한 구성원)다.
 */
data class TeamMatchAccepted(
	val teamMatchId: Long,
	val recipientUserIds: List<Long>,
)
