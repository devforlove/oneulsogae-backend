package com.org.oneulsogae.core.teammatch.command.domain.event

import com.org.oneulsogae.core.teammatch.command.domain.Team

/**
 * 팀에 초대했을 때 발행되는 도메인 이벤트.
 * 수신측(알람 저장 등)이 후속 처리에 필요한 식별 정보만 담는다.
 * [invitedUserId]는 초대받은 사람(알람 수신자), [inviterUserId]는 초대한 사람, [teamId]는 결성된 팀이다.
 */
data class TeamInvitationSent(
	val teamId: Long,
	val inviterUserId: Long,
	val invitedUserId: Long,
) {

	companion object {

		/** 결성된 팀과 초대자·초대 대상으로부터 이벤트를 만든다. */
		fun from(team: Team, inviterUserId: Long, invitedUserId: Long): TeamInvitationSent =
			TeamInvitationSent(
				teamId = team.id,
				inviterUserId = inviterUserId,
				invitedUserId = invitedUserId,
			)
	}
}
