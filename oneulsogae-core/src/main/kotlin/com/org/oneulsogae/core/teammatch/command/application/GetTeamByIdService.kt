package com.org.oneulsogae.core.teammatch.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.teammatch.TeamErrorCode
import com.org.oneulsogae.core.teammatch.command.application.port.`in`.GetTeamByIdUseCase
import com.org.oneulsogae.core.teammatch.command.application.port.out.GetTeamPort
import com.org.oneulsogae.core.teammatch.command.domain.Team
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetTeamByIdUseCase] 구현. 팀 조회 아웃포트([GetTeamPort])로 팀을 읽고, 없으면 [TeamErrorCode.TEAM_NOT_FOUND]를 던진다.
 */
@Service
class GetTeamByIdService(
	private val getTeamPort: GetTeamPort,
) : GetTeamByIdUseCase {

	@Transactional(readOnly = true)
	override fun getById(teamId: Long): Team =
		getTeamPort.findById(teamId)
			?: throw BusinessException(TeamErrorCode.TEAM_NOT_FOUND)
}
