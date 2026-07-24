package com.org.oneulsogae.infra.teammatch.command.mapper

import com.org.oneulsogae.core.teammatch.command.domain.MatchedTeams
import com.org.oneulsogae.core.teammatch.command.domain.TeamMatch
import com.org.oneulsogae.infra.teammatch.command.entity.TeamMatchEntity
import java.time.LocalDateTime

/** 영속성 엔티티 -> 도메인 모델. 참가 팀([MatchedTeams])은 어댑터가 조회해 함께 넘긴다. */
fun TeamMatchEntity.toDomain(matchedTeams: MatchedTeams): TeamMatch =
	TeamMatch(
		id = id ?: 0,
		version = version,
		matchedTeams = matchedTeams,
		introducedDate = introducedDate,
		expiresAt = expiresAt,
		matchType = matchType,
		status = status,
		deletedAt = deletedAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티(헤더). 참가 팀 조합 키는 [TeamMatch.memberKey]에서 산출한다.
 * id가 0이면 신규 저장(INSERT), 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 * deletedAt이 있으면 소프트 삭제 상태로 마킹한다.
 */
fun TeamMatch.toEntity(): TeamMatchEntity =
	TeamMatchEntity(
		memberKey = memberKey(),
		introducedDate = introducedDate,
		expiresAt = expiresAt,
		status = status,
		matchType = matchType,
	).also {
		if (id != 0L) it.id = id
		// 읽은 시점의 버전을 그대로 실어, merge 시 낙관적 락이 그 버전 기준으로 충돌을 검사하도록 한다.
		it.version = version
		deletedAt?.let { at: LocalDateTime -> it.softDelete(at) }
	}
