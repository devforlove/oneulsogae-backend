package com.org.oneulsogae.admin.universityverification.query.dto

import com.org.oneulsogae.common.user.UniversityImageVerificationStatus
import java.time.LocalDateTime

/**
 * 어드민 학교 이미지 인증 상세 read model. 목록 필드 + 사용자가 주장한 학교 정보(universityEmail) +
 * 제출 시점의 이전 학교명([previousUniversityName])·유저가 제출 시 기입한 희망 학교명([requestedUniversityName])·어드민 반려 사유([rejectionReason]).
 * dao는 [imageKey]까지 채우고 [imageUrl]은 null로 둔다. 서비스가 presign 결과로 [imageUrl]을 채운다.
 * (QueryDSL Projections.constructor가 imageUrl 없이 투영할 수 있도록 11-arg 보조 생성자를 둔다)
 */
data class AdminUniversityVerificationDetailView(
	val id: Long,
	val userId: Long,
	val nickname: String?,
	val email: String?,
	val status: UniversityImageVerificationStatus,
	val createdAt: LocalDateTime?,
	val imageKey: String,
	/** 제출 시점에 스냅샷한 이전(기존 프로필) 학교명. (university_image_verifications.previous_university_name) */
	val previousUniversityName: String?,
	val universityEmail: String?,
	/** 유저가 제출 시 기입한 희망 학교명. (university_image_verifications.university_name) */
	val requestedUniversityName: String?,
	/** 어드민 반려 사유. */
	val rejectionReason: String?,
	val imageUrl: String? = null,
) {
	/** dao 투영용 생성자. imageUrl은 서비스가 presign으로 채운다. */
	constructor(
		id: Long,
		userId: Long,
		nickname: String?,
		email: String?,
		status: UniversityImageVerificationStatus,
		createdAt: LocalDateTime?,
		imageKey: String,
		previousUniversityName: String?,
		universityEmail: String?,
		requestedUniversityName: String?,
		rejectionReason: String?,
	) : this(id, userId, nickname, email, status, createdAt, imageKey, previousUniversityName, universityEmail, requestedUniversityName, rejectionReason, null)
}
