package com.org.meeple.core.match.command.domain

/**
 * 한 팀 매칭([TeamMatch])에 참가한 팀([MatchedTeam]) 목록의 일급 컬렉션.
 * 참가 팀 식별과, 재소개 방지에 쓰는 멤버 키(정렬된 team-id 결합) 산출을 한곳에 응집한다.
 */
data class MatchedTeams(
	val values: List<MatchedTeam>,
) {

	/** 참가 팀 수. */
	val size: Int
		get() = values.size

	/** 참가 팀 id 목록. */
	fun teamIds(): List<Long> =
		values.map { matchedTeam: MatchedTeam -> matchedTeam.teamId }

	/** 두 팀 조합을 식별하는 정규화 키. (순서와 무관하게 같은 조합이면 같은 키) */
	fun memberKey(): String =
		teamIds().sorted().joinToString("-")

	/** 모든 참가 팀에 소속 팀 매칭 id([teamMatchId])를 채운 새 컬렉션. (헤더 저장으로 id를 얻은 뒤 영속화 직전에 호출) */
	fun withTeamMatchId(teamMatchId: Long): MatchedTeams =
		MatchedTeams(values.map { matchedTeam: MatchedTeam -> matchedTeam.copy(teamMatchId = teamMatchId) })

	companion object {

		/** teamId들로 참가 팀 목록을 만든다. (teamMatchId는 저장 시 채워진다) */
		fun of(teamIds: List<Long>): MatchedTeams =
			MatchedTeams(
				teamIds.map { teamId: Long -> MatchedTeam(teamMatchId = 0, teamId = teamId) },
			)
	}
}
