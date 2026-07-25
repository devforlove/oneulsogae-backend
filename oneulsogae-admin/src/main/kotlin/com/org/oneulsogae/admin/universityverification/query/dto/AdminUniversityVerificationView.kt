package com.org.oneulsogae.admin.universityverification.query.dto

import com.org.oneulsogae.common.user.UniversityImageVerificationStatus
import java.time.LocalDateTime

/**
 * 어드민 학교 이미지 인증 목록 한 건(read model).
 * dao는 [imageKey]까지 채우고 [imageUrl]은 null로 둔다. 서비스가 presign 결과로 [imageUrl]을 채운다.
 * (QueryDSL Projections.constructor가 imageUrl 없이 투영할 수 있도록 7-arg 보조 생성자를 둔다)
 */
data class AdminUniversityVerificationView(
	val id: Long,
	val userId: Long,
	val nickname: String?,
	val email: String?,
	val status: UniversityImageVerificationStatus,
	val createdAt: LocalDateTime?,
	val imageKey: String,
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
	) : this(id, userId, nickname, email, status, createdAt, imageKey, null)
}
