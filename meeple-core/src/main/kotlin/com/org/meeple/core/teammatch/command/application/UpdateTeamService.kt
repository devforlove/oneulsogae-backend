package com.org.meeple.core.teammatch.command.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.lock.DistributedLock
import com.org.meeple.core.common.lock.LockKeyConstraints
import com.org.meeple.core.teammatch.TeamErrorCode
import com.org.meeple.core.teammatch.command.application.port.`in`.UpdateTeamUseCase
import com.org.meeple.core.teammatch.command.application.port.`in`.command.UpdateTeamCommand
import com.org.meeple.core.teammatch.command.application.port.out.GetTeamPort
import com.org.meeple.core.teammatch.command.application.port.out.SaveTeamPort
import com.org.meeple.core.teammatch.command.domain.Team
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [UpdateTeamUseCase] 구현. 진행 중(INVITING)이거나 결성(ACTIVE)된 팀의 표시 정보(이름·소개·활동지역)를 구성원이 수정한다.
 * 팀을 [Team.update]로 수정해 영속화한다. (상태·구성원은 바꾸지 않으므로 알림·시각 처리는 없다)
 * teamId 분산 락으로 해체·철회 등 동시 생애주기 변경과 직렬화해, 비활성화 중인 팀을 되살리는 갱신 손실을 막는다.
 */
@Service
class UpdateTeamService(
	private val getTeamPort: GetTeamPort,
	private val saveTeamPort: SaveTeamPort,
) : UpdateTeamUseCase {

	@DistributedLock(prefix = LockKeyConstraints.TEAM_LIFECYCLE, keys = ["#teamId"], waitTime = 0)
	@Transactional
	override fun update(userId: Long, teamId: Long, command: UpdateTeamCommand): Team {
		val team: Team = getTeamPort.findById(teamId)
			?: throw BusinessException(TeamErrorCode.TEAM_NOT_FOUND)
		return saveTeamPort.save(team.update(userId, command.name, command.introduction, command.regionId))
	}
}
