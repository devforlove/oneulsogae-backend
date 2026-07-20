package com.org.oneulsogae.core.teammatch.command.domain

/**
 * 한 팀 매칭에 참가한 팀([Team])들의 일급 컬렉션(first-class collection).
 * 여러 팀에 걸친 조회(행위자 팀 식별·상대 팀 구성원·전체 참가 구성원)를 한곳에 응집한다. (2:2 매칭은 두 팀을 담는다)
 */
data class Teams(
	val values: List<Team>,
) {

	/** [userId]가 활성(ACTIVE) 구성원으로 속한 팀을 찾는다. 없으면 null. (행위자 팀 식별 = 참가 검증) */
	fun findByActiveMember(userId: Long): Team? =
		values.firstOrNull { team: Team -> userId in team.activeMemberIds() }

	/** 모든 팀의 활성(ACTIVE) 구성원. 각 구성원은 소속 teamId를 보유한다. (채팅방 참가자 구성용) */
	fun activeMembers(): List<TeamMember> =
		values.flatMap { team: Team -> team.activeMembers() }

	/** [actorTeamId] 팀을 제외한 상대 팀들의 활성(ACTIVE) 구성원 userId. (관심 받음 알림 수신자) */
	fun opponentActiveMemberIds(actorTeamId: Long): List<Long> =
		values.filter { team: Team -> team.id != actorTeamId }
			.flatMap { team: Team -> team.activeMemberIds() }

	/** [teamId] 팀의 상대 팀 id. (2:2 매칭이라 상대는 정확히 한 팀) */
	fun opponentTeamId(teamId: Long): Long =
		values.first { team: Team -> team.id != teamId }.id

	/** 성사된 두 팀의 ACTIVE 구성원마다 (그 구성원 → 상대 팀 id) 이력을 만든다. (재매칭 제외 기록용) */
	fun matchHistories(): List<RecommendedTeamHistory> =
		values.flatMap { team: Team ->
			team.activeMemberIds().map { userId: Long -> RecommendedTeamHistory(userId = userId, teamId = opponentTeamId(team.id)) }
		}
}
