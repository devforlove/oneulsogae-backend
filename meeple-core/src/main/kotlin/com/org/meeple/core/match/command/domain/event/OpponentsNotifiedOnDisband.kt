package com.org.meeple.core.match.command.domain.event

/**
 * 팀 해체로 진행 중 매칭이 정리될 때, 상대 팀 활성 구성원에게 알림을 보내기 위해 발행되는 도메인 이벤트.
 * 알림은 부가 효과이므로 수신측([com.org.meeple.core.match.command.application.TeamEventHandler])이 커밋 이후 처리한다.
 * [disbandedTeamId]는 해체된 팀(알림 유발 팀), [recipientUserIds]는 알림 수신자(상대 팀 활성 구성원) userId들이다.
 */
data class OpponentsNotifiedOnDisband(
	val disbandedTeamId: Long,
	val recipientUserIds: List<Long>,
)
