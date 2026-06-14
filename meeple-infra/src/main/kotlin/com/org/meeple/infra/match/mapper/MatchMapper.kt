package com.org.meeple.infra.match.mapper

import com.org.meeple.core.match.domain.Match
import com.org.meeple.infra.match.entity.MatchEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun MatchEntity.toDomain(): Match =
	Match(
		id = id ?: 0,
		maleUserId = maleUserId,
		femaleUserId = femaleUserId,
		introducedDate = introducedDate,
		expiresAt = expiresAt,
		matchType = matchType,
		maleAccepted = maleAccepted,
		femaleAccepted = femaleAccepted,
		status = status,
		datingInitAmount = dateInitAmount,
		datingAcceptAmount = dateAcceptAmount,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun Match.toEntity(): MatchEntity =
	MatchEntity(
		maleUserId = maleUserId,
		femaleUserId = femaleUserId,
		introducedDate = introducedDate,
		expiresAt = expiresAt,
		matchType = matchType,
		maleAccepted = maleAccepted,
		femaleAccepted = femaleAccepted,
		status = status,
		dateInitAmount = datingInitAmount,
		dateAcceptAmount = datingAcceptAmount,
	).also { if (id != 0L) it.id = id }
