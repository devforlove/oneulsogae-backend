package com.org.meeple.core.match.command.application.port.`in`

import com.org.meeple.core.match.command.application.port.`in`.command.InviteTeamCommand
import com.org.meeple.core.match.command.domain.Team

/**
 * 팀 초대(결성) 유스케이스(인포트).
 * 초대자([ownerId])가 다른 사용자를 초대해 팀을 결성한다. (초대 대상은 즉시 구성원으로 합류)
 */
interface InviteTeamUseCase {

	/** [ownerId]가 [command]의 대상을 초대해 팀을 결성하고, 저장된 팀을 반환한다. */
	fun invite(ownerId: Long, command: InviteTeamCommand): Team
}
