package com.org.meeple.core.teammatch.command.domain.event

/**
 * 팀이 해체될 때, 해체를 실행한 구성원을 제외한 같은 팀의 남은 구성원에게 알림을 보내기 위해 발행되는 도메인 이벤트.
 * 알림은 부가 효과이므로 수신측([com.org.meeple.core.teammatch.command.application.TeamEventHandler])이 커밋 이후 처리한다.
 * [disbandedByUserId]는 해체를 실행한 구성원(알림의 발신 유저), [recipientUserIds]는 알림 수신자(해체 실행자를 제외한 남은 팀 구성원) userId들이다.
 */
data class TeamDisbanded(
	val disbandedByUserId: Long,
	val recipientUserIds: List<Long>,
)
