package com.org.meeple.core.match.command.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.lock.DistributedLock
import com.org.meeple.core.common.lock.LockKeyConstraints
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.match.TeamErrorCode
import com.org.meeple.core.match.command.application.port.`in`.DisbandTeamUseCase
import com.org.meeple.core.match.command.application.port.out.GetTeamPort
import com.org.meeple.core.match.command.application.port.out.SaveTeamPort
import com.org.meeple.core.match.command.domain.Team
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [DisbandTeamUseCase] 구현. FORMED 팀을 구성원이 해체한다.
 * 팀을 조회해 [Team.disband]로 비활성화(DEACTIVATED + soft delete)한 뒤 저장한다.
 * 비활성화 시각은 [TimeGenerator]로 얻어 도메인에 주입한다. (LocalDateTime.now() 직접 호출 금지 — 테스트에서 시각 고정 가능)
 * teamId 분산 락으로 동시 상태 변경과 직렬화한다.
 */
@Service
class DisbandTeamService(
	private val getTeamPort: GetTeamPort,
	private val saveTeamPort: SaveTeamPort,
	private val timeGenerator: TimeGenerator,
) : DisbandTeamUseCase {

	@DistributedLock(prefix = LockKeyConstraints.TEAM_LIFECYCLE, keys = ["#teamId"], waitTime = 0)
	@Transactional
	override fun disband(userId: Long, teamId: Long): Team {
		val team: Team = getTeamPort.findById(teamId)
			?: throw BusinessException(TeamErrorCode.TEAM_NOT_FOUND)
		return saveTeamPort.save(team.disband(userId, timeGenerator.now()))
	}
}
