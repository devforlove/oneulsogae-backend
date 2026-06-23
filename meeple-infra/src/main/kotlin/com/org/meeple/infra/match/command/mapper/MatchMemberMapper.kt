package com.org.meeple.infra.match.command.mapper

import com.org.meeple.core.match.command.domain.MatchMember
import com.org.meeple.infra.match.command.entity.SoloMatchMemberEntity
import java.time.LocalDateTime

/** 영속성 엔티티 -> 도메인 모델 */
fun SoloMatchMemberEntity.toDomain(): MatchMember =
	MatchMember(
		id = id ?: 0,
		matchId = matchId,
		userId = userId,
		gender = gender,
		status = status,
		deletedAt = deletedAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 * deletedAt이 있으면 소프트 삭제 상태로 마킹한다. (deleted_at은 BaseEntity가 protected로 관리하므로 softDelete로 적용)
 */
fun MatchMember.toEntity(): SoloMatchMemberEntity =
	SoloMatchMemberEntity(
		matchId = matchId,
		userId = userId,
		gender = gender,
		status = status,
	).also {
		if (id != 0L) it.id = id
		deletedAt?.let { at: LocalDateTime -> it.softDelete(at) }
	}
