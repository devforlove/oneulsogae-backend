package com.org.meeple.core.match.command.domain.event

/**
 * 팀이 해체될 때, 해체를 실행한 구성원을 제외한 같은 팀의 남은 구성원에게 알림을 보내기 위해 발행되는 도메인 이벤트.
 * 알림은 부가 효과이므로 수신측([com.org.meeple.core.match.command.application.TeamEventHandler])이 커밋 이후 처리한다.
 * [disbandedTeamId]는 해체된 팀, [recipientUserIds]는 알림 수신자(해체 실행자를 제외한 남은 팀 구성원) userId들이다.
 */
data class TeamDisbanded(
	val disbandedTeamId: Long,
	val recipientUserIds: List<Long>,
)
