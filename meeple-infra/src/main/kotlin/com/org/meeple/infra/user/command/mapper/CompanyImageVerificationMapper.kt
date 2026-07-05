package com.org.meeple.infra.user.command.mapper

import com.org.meeple.core.user.command.domain.CompanyImageVerification
import com.org.meeple.infra.user.command.entity.CompanyImageVerificationEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun CompanyImageVerificationEntity.toDomain(): CompanyImageVerification =
	CompanyImageVerification(
		id = id ?: 0,
		userId = userId,
		imageKey = imageKey,
		status = status,
		companyName = companyName,
		previousCompanyName = previousCompanyName,
		rejectionReason = rejectionReason,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun CompanyImageVerification.toEntity(): CompanyImageVerificationEntity =
	CompanyImageVerificationEntity(
		userId = userId,
		imageKey = imageKey,
		status = status,
		companyName = companyName,
		previousCompanyName = previousCompanyName,
		rejectionReason = rejectionReason,
	).also { if (id != 0L) it.id = id }
