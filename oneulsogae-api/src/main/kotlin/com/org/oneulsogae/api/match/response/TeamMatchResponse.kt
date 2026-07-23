package com.org.oneulsogae.api.match.response

import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.common.match.MatchedTeamStatus
import com.org.oneulsogae.core.teammatch.command.domain.MatchedTeam
import com.org.oneulsogae.core.teammatch.command.domain.TeamMatch
import java.time.LocalDateTime

/**
 * 팀 매칭 관심/수락 결과 응답. 매칭 상태·만료 시각과 참가 팀별 상태를 담는다.
 */
data class TeamMatchResponse(
	val teamMatchId: Long,
	val status: MatchStatus,
	/** 이 팀 매칭의 만료 시각. 성사(MATCHED)되면 사실상 만료되지 않도록 먼 미래로 연장된 값이 내려간다. */
	val expiresAt: LocalDateTime,
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
				expiresAt = teamMatch.expiresAt,
				matchedTeams = teamMatch.matchedTeams.values.map { matchedTeam: MatchedTeam ->
					MatchedTeamView(teamId = matchedTeam.teamId, status = matchedTeam.status)
				},
			)
	}
}
