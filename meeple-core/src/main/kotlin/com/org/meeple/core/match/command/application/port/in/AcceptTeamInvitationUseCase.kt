package com.org.meeple.core.match.command.application.port.`in`

import com.org.meeple.core.match.command.domain.Team

/**
 * 팀 초대 수락 유스케이스(인포트).
 * 초대받은 사용자([userId])가 팀([teamId]) 초대를 수락해 구성원이 되고, 전원 수락 시 팀이 결성(ACTIVE)된다.
 */
interface AcceptTeamInvitationUseCase {

	/** [userId]가 [teamId] 팀 초대를 수락하고, 갱신된 팀을 반환한다. */
	fun accept(userId: Long, teamId: Long): Team
}
