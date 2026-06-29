package com.org.meeple.core.teammatch.command.application.port.`in`

import com.org.meeple.core.teammatch.command.domain.Team

/**
 * 팀 해체 유스케이스(인포트). 결성(ACTIVE)/해체중(DISBANDED) 팀에서 구성원 한 명이 떠난다.
 * 남은 팀원이 있으면 팀은 해체중(DISBANDED), 마지막 구성원이 떠나면 비활성화(DEACTIVATED)가 된다.
 */
interface DisbandTeamUseCase {

	/** [userId]가 [teamId] 팀에서 떠나고, 전이된 팀(DISBANDED 또는 DEACTIVATED)을 반환한다. */
	fun disband(userId: Long, teamId: Long): Team
}
