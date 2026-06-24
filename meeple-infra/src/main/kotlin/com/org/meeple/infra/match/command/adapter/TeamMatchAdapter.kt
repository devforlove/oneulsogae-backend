package com.org.meeple.infra.match.command.adapter

import com.org.meeple.core.match.command.application.port.out.SaveTeamMatchPort
import com.org.meeple.core.match.command.domain.MatchedTeams
import com.org.meeple.core.match.command.domain.TeamMatch
import com.org.meeple.infra.match.command.entity.TeamMatchEntity
import com.org.meeple.infra.match.command.mapper.toDomain
import com.org.meeple.infra.match.command.mapper.toEntity
import com.org.meeple.infra.match.command.repository.MatchedTeamJpaRepository
import com.org.meeple.infra.match.command.repository.TeamMatchJpaRepository
import org.springframework.stereotype.Component

/**
 * [TeamMatchEntity]의 command 영속성 어댑터. ([SaveTeamMatchPort] 구현)
 * 팀 매칭은 헤더(team_matches) + 참가 팀(matched_teams)으로 이뤄진 하나의 애그리거트이므로,
 * 이 어댑터가 두 테이블의 영속화를 함께 책임진다. (헤더 저장 → id 획득 → 그 id로 참가 팀 행 저장)
 */
@Component
class TeamMatchAdapter(
	private val teamMatchJpaRepository: TeamMatchJpaRepository,
	private val matchedTeamJpaRepository: MatchedTeamJpaRepository,
) : SaveTeamMatchPort {

	override fun save(teamMatch: TeamMatch): TeamMatch {
		val savedEntity: TeamMatchEntity = teamMatchJpaRepository.save(teamMatch.toEntity())
		val teamMatchId: Long = savedEntity.id!!
		val savedMatchedTeams: MatchedTeams = MatchedTeams(
			matchedTeamJpaRepository
				.saveAll(teamMatch.matchedTeams.values.map { it.copy(teamMatchId = teamMatchId).toEntity() })
				.map { it.toDomain() },
		)
		return savedEntity.toDomain(savedMatchedTeams)
	}
}
