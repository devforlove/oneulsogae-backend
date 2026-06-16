package com.org.meeple.infra.match.command.mapper

import com.org.meeple.core.match.command.domain.MatchMember
import com.org.meeple.infra.match.command.entity.MatchMemberEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun MatchMemberEntity.toDomain(): MatchMember =
	MatchMember(
		id = id ?: 0,
		matchId = matchId,
		userId = userId,
		gender = gender,
		accepted = accepted,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun MatchMember.toEntity(): MatchMemberEntity =
	MatchMemberEntity(
		matchId = matchId,
		userId = userId,
		gender = gender,
		accepted = accepted,
	).also { if (id != 0L) it.id = id }
