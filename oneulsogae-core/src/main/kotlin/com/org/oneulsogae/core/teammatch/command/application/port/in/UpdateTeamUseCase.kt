package com.org.oneulsogae.core.teammatch.command.application.port.`in`

import com.org.oneulsogae.core.teammatch.command.application.port.`in`.command.UpdateTeamCommand
import com.org.oneulsogae.core.teammatch.command.domain.Team

/**
 * 팀 정보 수정 유스케이스(인포트). 진행 중(INVITING)이거나 결성(ACTIVE)된 팀의 표시 정보(이름·소개·활동지역)를 수정한다.
 */
interface UpdateTeamUseCase {

	/** [userId]가 속한 [teamId] 팀의 정보를 [command]로 수정하고, 수정된 팀을 반환한다. */
	fun update(userId: Long, teamId: Long, command: UpdateTeamCommand): Team
}
