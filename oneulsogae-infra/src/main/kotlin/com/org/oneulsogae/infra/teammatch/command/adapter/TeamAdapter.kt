package com.org.oneulsogae.infra.teammatch.command.adapter

import com.org.oneulsogae.common.match.TeamMemberStatus
import com.org.oneulsogae.common.match.TeamStatus
import com.org.oneulsogae.core.teammatch.command.application.port.out.GetTeamPort
import com.org.oneulsogae.core.teammatch.command.application.port.out.SaveTeamPort
import com.org.oneulsogae.core.teammatch.command.domain.Team
import com.org.oneulsogae.core.teammatch.command.domain.TeamMembers
import com.org.oneulsogae.infra.teammatch.command.entity.TeamEntity
import com.org.oneulsogae.infra.teammatch.command.entity.TeamMemberEntity
import com.org.oneulsogae.infra.teammatch.command.mapper.toDomain
import com.org.oneulsogae.infra.teammatch.command.mapper.toEntities
import com.org.oneulsogae.infra.teammatch.command.mapper.toEntity
import com.org.oneulsogae.infra.teammatch.command.repository.TeamJpaRepository
import com.org.oneulsogae.infra.teammatch.command.repository.TeamMemberJpaRepository
import org.springframework.stereotype.Component

/**
 * [TeamEntity]의 command 영속성 어댑터. (팀은 헤더(teams) + 구성원(team_members)으로 이뤄진 하나의 애그리거트)
 * 이 어댑터가 두 테이블의 영속화·조회를 함께 책임진다. core는 팀 저장([SaveTeamPort])과 조회([GetTeamPort])를 쓴다.
 */
@Component
class TeamAdapter(
	private val teamJpaRepository: TeamJpaRepository,
	private val teamMemberJpaRepository: TeamMemberJpaRepository,
) : SaveTeamPort, GetTeamPort {

	/**
	 * 팀 애그리거트를 저장한다. 헤더를 저장해 id를 얻고, 그 id로 구성원 행들을 함께 저장한다.
	 * 신규면 구성원이 INSERT(member id 0)된다.
	 */
	override fun save(team: Team): Team {
		val savedEntity: TeamEntity = teamJpaRepository.save(team.toEntity())
		val teamId: Long = savedEntity.id!!
		val savedMembers: TeamMembers = TeamMembers(
			teamMemberJpaRepository
				.saveAll(team.membersWith(teamId).toEntities())
				.map { it.toDomain() },
		)
		return savedEntity.toDomain(savedMembers)
	}

	/** 팀 헤더와 구성원 행들을 함께 조회해 도메인으로 조립한다. 헤더가 없으면 null. */
	override fun findById(teamId: Long): Team? {
		val teamEntity: TeamEntity = teamJpaRepository.findById(teamId).orElse(null) ?: return null
		val members: TeamMembers = TeamMembers(
			teamMemberJpaRepository.findByTeamId(teamId).map { entity: TeamMemberEntity -> entity.toDomain() },
		)
		return teamEntity.toDomain(members)
	}

	/** 구성원 상태가 ACTIVE인 team_member 행 존재 여부로 활성 팀 소속을 판정한다. (초대중(INVITED)은 여러 팀에서 가능하므로 제외) */
	override fun existsActiveTeamMember(userId: Long): Boolean =
		teamMemberJpaRepository.existsByUserIdAndStatus(userId, TeamMemberStatus.ACTIVE)

	/**
	 * userId의 (삭제되지 않은) team_member 행들을 상태 무관으로 찾아 각 팀 애그리거트로 조립한 뒤 INVITING 팀만 남긴다.
	 * 받은 초대(INVITED)뿐 아니라 내가 만든 초대(owner=ACTIVE) 팀도 포함되며, 이미 결성(ACTIVE)된 팀은 제외된다.
	 */
	override fun findInvitingTeamsByMember(userId: Long): List<Team> =
		teamMemberJpaRepository.findByUserId(userId)
			.mapNotNull { member: TeamMemberEntity -> findById(member.teamId) }
			.filter { team: Team -> team.status == TeamStatus.INVITING }
}
