package com.org.oneulsogae.infra.user.command.mapper

import com.org.oneulsogae.core.user.command.domain.UniversityImageVerification
import com.org.oneulsogae.infra.user.command.entity.UniversityImageVerificationEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun UniversityImageVerificationEntity.toDomain(): UniversityImageVerification =
	UniversityImageVerification(
		id = id ?: 0,
		userId = userId,
		imageKey = imageKey,
		status = status,
		universityName = universityName,
		previousUniversityName = previousUniversityName,
		rejectionReason = rejectionReason,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun UniversityImageVerification.toEntity(): UniversityImageVerificationEntity =
	UniversityImageVerificationEntity(
		userId = userId,
		imageKey = imageKey,
		status = status,
		universityName = universityName,
		previousUniversityName = previousUniversityName,
		rejectionReason = rejectionReason,
	).also { if (id != 0L) it.id = id }
