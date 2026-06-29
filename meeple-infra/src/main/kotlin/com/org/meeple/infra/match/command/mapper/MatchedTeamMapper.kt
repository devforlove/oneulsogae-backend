package com.org.meeple.infra.match.command.mapper

import com.org.meeple.core.match.command.domain.MatchedTeam
import com.org.meeple.core.match.command.domain.MatchedTeams
import com.org.meeple.infra.match.command.entity.MatchedTeamEntity
import java.time.LocalDateTime

/** 영속성 엔티티 -> 도메인 모델. */
fun MatchedTeamEntity.toDomain(): MatchedTeam =
	MatchedTeam(
		id = id ?: 0,
		teamMatchId = teamMatchId,
		teamId = teamId,
		status = status,
		applicantUserId = applicantUserId,
		deletedAt = deletedAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규 저장(INSERT), 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 * deletedAt이 있으면 소프트 삭제 상태로 마킹한다.
 */
fun MatchedTeam.toEntity(): MatchedTeamEntity =
	MatchedTeamEntity(
		teamMatchId = teamMatchId,
		teamId = teamId,
		status = status,
		applicantUserId = applicantUserId,
	).also {
		if (id != 0L) it.id = id
		deletedAt?.let { at: LocalDateTime -> it.softDelete(at) }
	}

/** 참가 팀 일급 컬렉션 -> 영속성 엔티티 목록. (어댑터가 [MatchedTeams.values]를 직접 들추지 않도록 변환을 캡슐화) */
fun MatchedTeams.toEntities(): List<MatchedTeamEntity> =
	values.map { matchedTeam: MatchedTeam -> matchedTeam.toEntity() }
