package com.org.meeple.api.match.response

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.core.match.command.domain.MatchedTeam
import com.org.meeple.core.match.command.domain.TeamMatch

/**
 * 팀 매칭 관심/수락 결과 응답. 매칭 상태와 참가 팀별 상태를 담는다.
 */
data class TeamMatchResponse(
	val teamMatchId: Long,
	val status: MatchStatus,
	val matchedTeams: List<MatchedTeamView>,
) {

	data class MatchedTeamView(
		val teamId: Long,
		val status: MatchedTeamStatus,
	)

	companion object {

		fun of(teamMatch: TeamMatch): TeamMatchResponse =
			TeamMatchResponse(
				teamMatchId = teamMatch.id,
				status = teamMatch.status,
				matchedTeams = teamMatch.matchedTeams.values.map { matchedTeam: MatchedTeam ->
					MatchedTeamView(teamId = matchedTeam.teamId, status = matchedTeam.status)
				},
			)
	}
}
