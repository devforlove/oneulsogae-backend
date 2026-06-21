package com.org.meeple.core.match.command.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.lock.DistributedLock
import com.org.meeple.core.common.lock.LockKeyConstraints
import com.org.meeple.core.match.TeamErrorCode
import com.org.meeple.core.match.command.application.port.`in`.AcceptTeamInvitationUseCase
import com.org.meeple.core.match.command.application.port.out.GetTeamPort
import com.org.meeple.core.match.command.application.port.out.SaveTeamPort
import com.org.meeple.core.match.command.domain.Team
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [AcceptTeamInvitationUseCase] 구현. 초대받은 사용자가 팀 초대를 수락한다.
 * 팀을 조회해 [Team.acceptInvitation]으로 상태를 전이(전원 수락 시 FORMED)한 뒤 저장한다.
 * 수락(invited)↔초대취소(owner) 동시 요청 경합을 막기 위해 teamId 분산 락으로 직렬화한다. (waitTime=0)
 */
@Service
class AcceptTeamInvitationService(
	private val getTeamPort: GetTeamPort,
	private val saveTeamPort: SaveTeamPort,
) : AcceptTeamInvitationUseCase {

	@DistributedLock(prefix = LockKeyConstraints.TEAM_LIFECYCLE, keys = ["#teamId"], waitTime = 0)
	@Transactional
	override fun accept(userId: Long, teamId: Long): Team {
		val team: Team = getTeamPort.findById(teamId)
			?: throw BusinessException(TeamErrorCode.TEAM_NOT_FOUND)
		return saveTeamPort.save(team.acceptInvitation(userId))
	}
}
