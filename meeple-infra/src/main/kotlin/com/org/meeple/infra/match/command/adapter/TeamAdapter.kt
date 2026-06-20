package com.org.meeple.infra.match.command.adapter

import com.org.meeple.core.match.command.application.port.out.SaveTeamPort
import com.org.meeple.core.match.command.domain.Team
import com.org.meeple.core.match.command.domain.TeamMembers
import com.org.meeple.infra.match.command.entity.TeamEntity
import com.org.meeple.infra.match.command.mapper.toDomain
import com.org.meeple.infra.match.command.mapper.toEntity
import com.org.meeple.infra.match.command.repository.TeamJpaRepository
import com.org.meeple.infra.match.command.repository.TeamMemberJpaRepository
import org.springframework.stereotype.Component

/**
 * [TeamEntity]의 command 영속성 어댑터. (팀은 헤더(teams) + 구성원(team_members)으로 이뤄진 하나의 애그리거트)
 * 이 어댑터가 두 테이블의 영속화를 함께 책임진다. core는 팀 저장([SaveTeamPort])을 쓴다.
 */
@Component
class TeamAdapter(
	private val teamJpaRepository: TeamJpaRepository,
	private val teamMemberJpaRepository: TeamMemberJpaRepository,
) : SaveTeamPort {

	/**
	 * 팀 애그리거트를 저장한다. 헤더를 저장해 id를 얻고, 그 id로 구성원 행들을 함께 저장한다.
	 * 신규면 구성원이 INSERT(member id 0)된다.
	 */
	override fun save(team: Team): Team {
		val savedEntity: TeamEntity = teamJpaRepository.save(team.toEntity())
		val teamId: Long = savedEntity.id!!
		val savedMembers: TeamMembers = TeamMembers(
			teamMemberJpaRepository
				.saveAll(team.members.values.map { it.copy(teamId = teamId).toEntity() })
				.map { it.toDomain() },
		)
		return savedEntity.toDomain(savedMembers)
	}
}
