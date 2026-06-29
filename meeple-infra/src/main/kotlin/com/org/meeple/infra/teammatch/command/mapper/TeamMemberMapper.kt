package com.org.meeple.infra.teammatch.command.mapper

import com.org.meeple.core.teammatch.command.domain.TeamMember
import com.org.meeple.core.teammatch.command.domain.TeamMembers
import com.org.meeple.infra.teammatch.command.entity.TeamMemberEntity
import java.time.LocalDateTime

/** 영속성 엔티티 -> 도메인 모델 */
fun TeamMemberEntity.toDomain(): TeamMember =
	TeamMember(
		id = id ?: 0,
		teamId = teamId,
		userId = userId,
		status = status,
		deletedAt = deletedAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 * deletedAt이 있으면 소프트 삭제 상태로 마킹한다. (deleted_at은 BaseEntity가 protected로 관리하므로 softDelete로 적용)
 */
fun TeamMember.toEntity(): TeamMemberEntity =
	TeamMemberEntity(
		teamId = teamId,
		userId = userId,
		status = status,
	).also {
		if (id != 0L) it.id = id
		deletedAt?.let { at: LocalDateTime -> it.softDelete(at) }
	}

/** 구성원 일급 컬렉션 -> 영속성 엔티티 목록. (어댑터가 [TeamMembers.values]를 직접 들추지 않도록 변환을 캡슐화) */
fun TeamMembers.toEntities(): List<TeamMemberEntity> =
	values.map { member: TeamMember -> member.toEntity() }
