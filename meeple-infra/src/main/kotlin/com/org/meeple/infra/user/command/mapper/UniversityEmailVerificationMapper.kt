package com.org.meeple.infra.user.command.mapper

import com.org.meeple.core.user.command.domain.UniversityEmailVerification
import com.org.meeple.infra.user.command.entity.UniversityEmailVerificationEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun UniversityEmailVerificationEntity.toDomain(): UniversityEmailVerification =
	UniversityEmailVerification(
		id = id ?: 0,
		userId = userId,
		universityEmail = universityEmail,
		code = code,
		expiresAt = expiresAt,
		verifiedAt = verifiedAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun UniversityEmailVerification.toEntity(): UniversityEmailVerificationEntity =
	UniversityEmailVerificationEntity(
		userId = userId,
		universityEmail = universityEmail,
		code = code,
		expiresAt = expiresAt,
		verifiedAt = verifiedAt,
	).also { if (id != 0L) it.id = id }
