package com.org.oneulsogae.infra.teammatch.command.adapter

import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.common.match.TeamMatchType
import com.org.oneulsogae.core.teammatch.command.application.port.out.GetTeamMatchPort
import com.org.oneulsogae.core.teammatch.command.application.port.out.SaveTeamMatchPort
import com.org.oneulsogae.core.teammatch.command.domain.MatchedTeams
import com.org.oneulsogae.core.teammatch.command.domain.TeamMatch
import com.org.oneulsogae.infra.teammatch.command.entity.MatchedTeamEntity
import com.org.oneulsogae.infra.teammatch.command.entity.TeamMatchEntity
import com.org.oneulsogae.infra.teammatch.command.mapper.toDomain
import com.org.oneulsogae.infra.teammatch.command.mapper.toEntities
import com.org.oneulsogae.infra.teammatch.command.mapper.toEntity
import com.org.oneulsogae.infra.teammatch.command.repository.MatchedTeamJpaRepository
import com.org.oneulsogae.infra.teammatch.command.repository.TeamMatchJpaRepository
import com.org.oneulsogae.scheduler.teammatch.command.application.port.out.SaveTeamMatchRecordPort
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [TeamMatchEntity]의 command 영속성 어댑터. ([SaveTeamMatchPort], [GetTeamMatchPort] 구현)
 * 팀 매칭은 헤더(team_matches) + 참가 팀(matched_teams)으로 이뤄진 하나의 애그리거트이므로,
 * 이 어댑터가 두 테이블의 영속화·조회를 함께 책임진다. (헤더 저장 → id 획득 → 그 id로 참가 팀 행 저장)
 * 같은 엔티티를 쓰는 scheduler의 배치 기록 아웃포트([SaveTeamMatchRecordPort])도 이 어댑터가 함께 구현한다.
 */
@Component
class TeamMatchAdapter(
	private val teamMatchJpaRepository: TeamMatchJpaRepository,
	private val matchedTeamJpaRepository: MatchedTeamJpaRepository,
	private val entityManager: EntityManager,
) : SaveTeamMatchPort, GetTeamMatchPort, SaveTeamMatchRecordPort {

	// 일일 팀 매칭 배치가 호출하는 경로 — DAILY 타입의 신규 소개(PROPOSED)로 기록한다.
	override fun saveProposedTeamMatch(teamAId: Long, teamBId: Long, now: LocalDateTime) {
		save(TeamMatch.propose(teamAId = teamAId, teamBId = teamBId, matchType = TeamMatchType.DAILY, now = now))
	}

	override fun save(teamMatch: TeamMatch): TeamMatch {
		val savedEntity: TeamMatchEntity = teamMatchJpaRepository.save(teamMatch.toEntity())
		// 기존 애그리거트 변경은 참가 팀(matched_teams)만 바뀌어 헤더가 dirty하지 않아도 헤더 버전을 강제로 올린다.
		// → 헤더+참가 팀(=팀 매칭 애그리거트 전체)에 대한 동시 변경을 헤더 한 행의 낙관적 락으로 직렬화한다. (예: 팀 탈퇴 ↔ 관심/수락)
		if (teamMatch.id != 0L) {
			entityManager.lock(savedEntity, LockModeType.OPTIMISTIC_FORCE_INCREMENT)
		}
		val teamMatchId: Long = savedEntity.id!!
		val savedMatchedTeams: MatchedTeams = MatchedTeams(
			matchedTeamJpaRepository
				.saveAll(teamMatch.matchedTeamsWith(teamMatchId).toEntities())
				.map { it.toDomain() },
		)
		return savedEntity.toDomain(savedMatchedTeams)
	}

	// 소프트삭제 포함 member_key 존재 검사. (재소개 방지 유니크와 같은 범위 — 추천 승격 전 중복 조합 건너뛰기용)
	override fun existsByMemberKey(memberKey: String): Boolean =
		teamMatchJpaRepository.countByMemberKeyIncludingDeleted(memberKey) > 0

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
