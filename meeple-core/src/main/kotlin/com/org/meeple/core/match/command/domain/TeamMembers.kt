package com.org.meeple.core.match.command.domain

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.user.Gender

/**
 * 한 팀의 구성원([TeamMember]) 목록의 일급 컬렉션(first-class collection).
 * 구성원 식별·소속 판정을 한곳에 응집한다. (2:2 팀은 두 명을 담는다)
 */
data class TeamMembers(
	val values: List<TeamMember>,
) {

	/** 구성원 수. */
	val size: Int
		get() = values.size

	/** [userId]가 이 팀의 구성원인지 여부. */
	fun isMember(userId: Long): Boolean =
		values.any { it.userId == userId }

	/** 구성원 userId 목록. */
	fun userIds(): List<Long> =
		values.map { it.userId }

	companion object {

		/** (userId, gender, status) 묶음들로 구성원 목록을 만든다. (teamId는 저장 시 채워진다) */
		fun of(participants: List<Triple<Long, Gender, TeamMemberStatus>>): TeamMembers =
			TeamMembers(
				participants.map { (userId: Long, gender: Gender, status: TeamMemberStatus) ->
					TeamMember(teamId = 0, userId = userId, gender = gender, status = status)
				},
			)
	}
}
