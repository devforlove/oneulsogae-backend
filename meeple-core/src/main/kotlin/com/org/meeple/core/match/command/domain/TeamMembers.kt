package com.org.meeple.core.match.command.domain

import com.org.meeple.common.match.TeamMemberStatus
import java.time.LocalDateTime

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

	/** [userId] 구성원을 찾는다. 없으면 null. */
	fun find(userId: Long): TeamMember? =
		values.firstOrNull { member: TeamMember -> member.userId == userId }

	/** 초대자(유일한 ACTIVE 구성원)의 userId. (초대 단계(INVITING) 팀 기준 — owner) */
	fun inviterId(): Long =
		values.first { member: TeamMember -> member.status == TeamMemberStatus.ACTIVE }.userId

	/** 초대 대상(유일한 INVITED 구성원)의 userId. (초대 단계(INVITING) 팀 기준) */
	fun invitedId(): Long =
		values.first { member: TeamMember -> member.status == TeamMemberStatus.INVITED }.userId

	/** [userId] 구성원만 ACTIVE로 전환한 새 컬렉션. (나머지는 그대로) */
	fun accept(userId: Long): TeamMembers =
		TeamMembers(
			values.map { member: TeamMember ->
				if (member.userId == userId) member.copy(status = TeamMemberStatus.ACTIVE) else member
			},
		)

	/** 모든 구성원이 ACTIVE인지 여부. */
	fun allActive(): Boolean =
		values.all { member: TeamMember -> member.status == TeamMemberStatus.ACTIVE }

	/** 전원을 비활성(DEACTIVE) + 소프트 삭제([now]) 표시한 새 컬렉션. (팀 해체·초대취소 시) */
	fun deactivateAll(now: LocalDateTime): TeamMembers =
		TeamMembers(
			values.map { member: TeamMember -> member.copy(status = TeamMemberStatus.DEACTIVE, deletedAt = now) },
		)

	companion object {

		/** (userId, status) 묶음들로 구성원 목록을 만든다. (teamId는 저장 시 채워진다. 성별은 [Team.gender]가 보관) */
		fun of(participants: List<Pair<Long, TeamMemberStatus>>): TeamMembers =
			TeamMembers(
				participants.map { (userId: Long, status: TeamMemberStatus) ->
					TeamMember(teamId = 0, userId = userId, status = status)
				},
			)
	}
}
