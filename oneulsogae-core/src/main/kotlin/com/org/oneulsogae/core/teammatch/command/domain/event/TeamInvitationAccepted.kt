package com.org.oneulsogae.core.teammatch.command.domain.event

/**
 * 초대받은 사람이 팀 초대를 수락했을 때 발행되는 도메인 이벤트.
 * 수신측(알람 저장 등)이 후속 처리에 필요한 식별 정보만 담는다.
 * [inviterUserId]는 초대한 사람(알람 수신자), [invitedUserId]는 수락한 사람, [teamId]는 수락된 팀이다.
 */
data class TeamInvitationAccepted(
	val teamId: Long,
	val inviterUserId: Long,
	val invitedUserId: Long,
)
