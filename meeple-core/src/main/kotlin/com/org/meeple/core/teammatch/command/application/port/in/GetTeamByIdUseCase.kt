package com.org.meeple.core.teammatch.command.application.port.`in`

import com.org.meeple.core.teammatch.command.domain.Team

/**
 * id로 팀(헤더+구성원)을 조회하는 인포트(유스케이스). 다른 도메인이 팀 존재를 확인할 때도 쓴다.
 * 없으면 [com.org.meeple.core.teammatch.TeamErrorCode.TEAM_NOT_FOUND]를 던진다.
 */
interface GetTeamByIdUseCase {

	fun getById(teamId: Long): Team
}
