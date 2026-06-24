package com.org.meeple.infra.match.command.adapter

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.core.match.command.application.port.out.GetTeamMatchPort
import com.org.meeple.core.match.command.application.port.out.SaveTeamMatchPort
import com.org.meeple.core.match.command.domain.MatchedTeams
import com.org.meeple.core.match.command.domain.TeamMatch
import com.org.meeple.infra.match.command.entity.MatchedTeamEntity
import com.org.meeple.infra.match.command.entity.TeamMatchEntity
import com.org.meeple.infra.match.command.mapper.toDomain
import com.org.meeple.infra.match.command.mapper.toEntities
import com.org.meeple.infra.match.command.mapper.toEntity
import com.org.meeple.infra.match.command.repository.MatchedTeamJpaRepository
import com.org.meeple.infra.match.command.repository.TeamMatchJpaRepository
import org.springframework.stereotype.Component

/**
 * [TeamMatchEntity]의 command 영속성 어댑터. ([SaveTeamMatchPort], [GetTeamMatchPort] 구현)
 * 팀 매칭은 헤더(team_matches) + 참가 팀(matched_teams)으로 이뤄진 하나의 애그리거트이므로,
 * 이 어댑터가 두 테이블의 영속화·조회를 함께 책임진다. (헤더 저장 → id 획득 → 그 id로 참가 팀 행 저장)
 */
@Component
class TeamMatchAdapter(
	private val teamMatchJpaRepository: TeamMatchJpaRepository,
	private val matchedTeamJpaRepository: MatchedTeamJpaRepository,
) : SaveTeamMatchPort, GetTeamMatchPort {

	override fun save(teamMatch: TeamMatch): TeamMatch {
		val savedEntity: TeamMatchEntity = teamMatchJpaRepository.save(teamMatch.toEntity())
		val teamMatchId: Long = savedEntity.id!!
		val savedMatchedTeams: MatchedTeams = MatchedTeams(
			matchedTeamJpaRepository
				.saveAll(teamMatch.matchedTeamsWith(teamMatchId).toEntities())
				.map { it.toDomain() },
		)
		return savedEntity.toDomain(savedMatchedTeams)
	}

	override fun findById(teamMatchId: Long): TeamMatch? {
		val header: TeamMatchEntity = teamMatchJpaRepository.findById(teamMatchId).orElse(null) ?: return null
		val matchedTeams: MatchedTeams = MatchedTeams(
			matchedTeamJpaRepository.findByTeamMatchIdIn(listOf(teamMatchId)).map { it.toDomain() },
		)
		return header.toDomain(matchedTeams)
	}

	override fun findActiveByTeamId(teamId: Long): List<TeamMatch> {
		// ① 이 팀의 참가 행으로 소속 팀 매칭 id 수집 (idx_team_id seek)
		val teamMatchIds: List<Long> = matchedTeamJpaRepository.findByTeamId(teamId)
			.map { it.teamMatchId }
			.distinct()
		if (teamMatchIds.isEmpty()) return emptyList()

		// ② 종료(CLOSED)되지 않은 헤더만 (PK IN seek)
		val headers: List<TeamMatchEntity> = teamMatchJpaRepository.findByIdInAndStatusNot(teamMatchIds, MatchStatus.CLOSED)
		if (headers.isEmpty()) return emptyList()

		// ③ 그 헤더들의 참가 팀 전원을 한 번에 로드해 헤더별로 묶는다 (ux_team_match_id_team_id 선두 seek)
		val headerIds: List<Long> = headers.map { it.id!! }
		val membersByMatchId: Map<Long, List<MatchedTeamEntity>> = matchedTeamJpaRepository.findByTeamMatchIdIn(headerIds)
			.groupBy { it.teamMatchId }

		return headers.map { header: TeamMatchEntity ->
			val matchedTeams: MatchedTeams = MatchedTeams(
				membersByMatchId[header.id!!].orEmpty().map { it.toDomain() },
			)
			header.toDomain(matchedTeams)
		}
	}
}
