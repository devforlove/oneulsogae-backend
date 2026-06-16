package com.org.meeple.infra.match.command.mapper

import com.org.meeple.core.match.command.domain.Match
import com.org.meeple.core.match.command.domain.MatchMembers
import com.org.meeple.infra.match.command.entity.MatchEntity

/**
 * 영속성 엔티티 -> 도메인 모델.
 * 참가자([MatchMembers])는 별도 테이블([com.org.meeple.infra.match.command.entity.MatchMemberEntity])이라 어댑터가 조회해 함께 넘긴다.
 */
fun MatchEntity.toDomain(members: MatchMembers): Match =
	Match(
		id = id ?: 0,
		members = members,
		introducedDate = introducedDate,
		expiresAt = expiresAt,
		matchType = matchType,
		status = status,
		datingInitAmount = dateInitAmount,
		datingAcceptAmount = dateAcceptAmount,
	)

/**
 * 도메인 모델 -> 영속성 엔티티(헤더).
 * 참가자 조합 키는 [Match.memberKey]에서 산출한다. (참가자 행 자체는 어댑터가 따로 저장한다)
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun Match.toEntity(): MatchEntity =
	MatchEntity(
		memberKey = memberKey(),
		introducedDate = introducedDate,
		expiresAt = expiresAt,
		matchType = matchType,
		status = status,
		dateInitAmount = datingInitAmount,
		dateAcceptAmount = datingAcceptAmount,
	).also { if (id != 0L) it.id = id }
