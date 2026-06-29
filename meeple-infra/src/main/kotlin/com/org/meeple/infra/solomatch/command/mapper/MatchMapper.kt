package com.org.meeple.infra.solomatch.command.mapper

import com.org.meeple.core.solomatch.command.domain.Match
import com.org.meeple.core.solomatch.command.domain.MatchMembers
import com.org.meeple.infra.solomatch.command.entity.SoloMatchEntity
import java.time.LocalDateTime

/**
 * 영속성 엔티티 -> 도메인 모델.
 * 참가자([MatchMembers])는 별도 테이블([com.org.meeple.infra.solomatch.command.entity.SoloMatchMemberEntity])이라 어댑터가 조회해 함께 넘긴다.
 */
fun SoloMatchEntity.toDomain(members: MatchMembers): Match =
	Match(
		id = id ?: 0,
		members = members,
		introducedDate = introducedDate,
		expiresAt = expiresAt,
		matchType = matchType,
		status = status,
		datingInitAmount = dateInitAmount,
		datingAcceptAmount = dateAcceptAmount,
		deletedAt = deletedAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티(헤더).
 * 참가자 조합 키는 [Match.memberKey]에서 산출한다. (참가자 행 자체는 어댑터가 따로 저장한다)
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 * deletedAt이 있으면 소프트 삭제 상태로 마킹한다. (deleted_at은 BaseEntity가 protected로 관리하므로 softDelete로 적용)
 */
fun Match.toEntity(): SoloMatchEntity =
	SoloMatchEntity(
		memberKey = memberKey(),
		introducedDate = introducedDate,
		expiresAt = expiresAt,
		matchType = matchType,
		status = status,
		dateInitAmount = datingInitAmount,
		dateAcceptAmount = datingAcceptAmount,
	).also {
		if (id != 0L) it.id = id
		deletedAt?.let { at: LocalDateTime -> it.softDelete(at) }
	}
