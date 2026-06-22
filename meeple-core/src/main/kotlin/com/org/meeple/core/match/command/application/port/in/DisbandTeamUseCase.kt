package com.org.meeple.core.match.command.application.port.`in`

import com.org.meeple.core.match.command.domain.Team

/**
 * 팀 해체 유스케이스(인포트). 결성(ACTIVE)된 팀의 구성원이 떠나면 팀 전체를 비활성화(DEACTIVATED)한다.
 */
interface DisbandTeamUseCase {

	/** [userId]가 속한 [teamId] 팀을 해체하고, 비활성화된 팀을 반환한다. */
	fun disband(userId: Long, teamId: Long): Team
}
