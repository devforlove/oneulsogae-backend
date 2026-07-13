package com.org.meeple.infra.user.command.mapper

import com.org.meeple.core.user.command.domain.MemberVerification
import com.org.meeple.infra.user.command.entity.MemberVerificationEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun MemberVerificationEntity.toDomain(): MemberVerification =
	MemberVerification(
		id = id ?: 0,
		userId = userId,
		jobCategory = jobCategory,
		jobDetail = jobDetail,
		faceImageKey = faceImageKey,
		bodyImageKey = bodyImageKey,
		documentImageKey = documentImageKey,
		status = status,
		rejectionReason = rejectionReason,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun MemberVerification.toEntity(): MemberVerificationEntity =
	MemberVerificationEntity(
		userId = userId,
		jobCategory = jobCategory,
		jobDetail = jobDetail,
		faceImageKey = faceImageKey,
		bodyImageKey = bodyImageKey,
		documentImageKey = documentImageKey,
		status = status,
		rejectionReason = rejectionReason,
	).also { if (id != 0L) it.id = id }
