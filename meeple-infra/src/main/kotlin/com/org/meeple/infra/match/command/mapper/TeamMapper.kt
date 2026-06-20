package com.org.meeple.infra.match.command.mapper

import com.org.meeple.core.match.command.domain.Team
import com.org.meeple.core.match.command.domain.TeamMembers
import com.org.meeple.infra.match.command.entity.TeamEntity
import java.time.LocalDateTime

/**
 * 영속성 엔티티 -> 도메인 모델.
 * 구성원([TeamMembers])은 별도 테이블([com.org.meeple.infra.match.command.entity.TeamMemberEntity])이라 어댑터가 조회해 함께 넘긴다.
 */
fun TeamEntity.toDomain(members: TeamMembers): Team =
	Team(
		id = id ?: 0,
		name = name,
		introduction = introduction,
		members = members,
		status = status,
		deletedAt = deletedAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티(헤더).
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 * deletedAt이 있으면 소프트 삭제 상태로 마킹한다. (deleted_at은 BaseEntity가 protected로 관리하므로 softDelete로 적용)
 */
fun Team.toEntity(): TeamEntity =
	TeamEntity(
		name = name,
		introduction = introduction,
		status = status,
	).also {
		if (id != 0L) it.id = id
		deletedAt?.let { at: LocalDateTime -> it.softDelete(at) }
	}
