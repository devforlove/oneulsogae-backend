package com.org.meeple.core.match.command.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.lock.DistributedLock
import com.org.meeple.core.common.lock.LockKeyConstraints
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.match.TeamErrorCode
import com.org.meeple.core.match.command.application.port.`in`.WithdrawTeamInvitationUseCase
import com.org.meeple.core.match.command.application.port.out.GetTeamPort
import com.org.meeple.core.match.command.application.port.out.SaveTeamPort
import com.org.meeple.core.match.command.domain.Team
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [WithdrawTeamInvitationUseCase] 구현. INVITING 팀의 초대를 철회(거절/취소)한다.
 * 팀을 조회해 [Team.withdrawInvitation]으로 비활성화(DEACTIVATED + soft delete)한 뒤 저장한다.
 * 비활성화 시각은 [TimeGenerator]로 얻어 도메인에 주입한다. (LocalDateTime.now() 직접 호출 금지 — 테스트에서 시각 고정 가능)
 * teamId 분산 락으로 수락 등 동시 상태 변경과 직렬화한다.
 */
@Service
class WithdrawTeamInvitationService(
	private val getTeamPort: GetTeamPort,
	private val saveTeamPort: SaveTeamPort,
	private val timeGenerator: TimeGenerator,
) : WithdrawTeamInvitationUseCase {

	@DistributedLock(prefix = LockKeyConstraints.TEAM_LIFECYCLE, keys = ["#teamId"], waitTime = 0)
	@Transactional
	override fun withdraw(userId: Long, teamId: Long): Team {
		val team: Team = getTeamPort.findById(teamId)
			?: throw BusinessException(TeamErrorCode.TEAM_NOT_FOUND)
		return saveTeamPort.save(team.withdrawInvitation(userId, timeGenerator.now()))
	}
}
