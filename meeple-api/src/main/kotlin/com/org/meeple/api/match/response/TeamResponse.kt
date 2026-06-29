package com.org.meeple.api.match.response

import com.org.meeple.common.match.TeamStatus
import com.org.meeple.core.teammatch.command.domain.Team

/**
 * 팀 결성(초대) 결과 응답. 결성된 팀의 식별자·이름·활동지역 id·소개·상태·구성원 userId를 담는다. (표시용 지역명은 regions 조회로 매핑)
 */
data class TeamResponse(
	val teamId: Long,
	val name: String,
	val regionId: Long,
	val introduction: String?,
	val status: TeamStatus,
	val memberUserIds: List<Long>,
) {
	companion object {
		fun of(team: Team): TeamResponse =
			TeamResponse(
				teamId = team.id,
				name = team.name,
				regionId = team.regionId,
				introduction = team.introduction,
				status = team.status,
				memberUserIds = team.members.userIds(),
			)
	}
}
