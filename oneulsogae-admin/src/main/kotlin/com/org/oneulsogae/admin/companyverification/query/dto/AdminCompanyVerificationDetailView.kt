package com.org.oneulsogae.admin.companyverification.query.dto

import com.org.oneulsogae.common.user.CompanyImageVerificationStatus
import java.time.LocalDateTime

/**
 * 어드민 회사 이미지 인증 상세 read model. 목록 필드 + 사용자가 주장한 직장 정보(companyEmail·job) +
 * 제출 시점의 이전 회사명([previousCompanyName])·유저가 제출 시 기입한 희망 회사명([requestedCompanyName])·어드민 반려 사유([rejectionReason]).
 * dao는 [imageKey]까지 채우고 [imageUrl]은 null로 둔다. 서비스가 presign 결과로 [imageUrl]을 채운다.
 * (QueryDSL Projections.constructor가 imageUrl 없이 투영할 수 있도록 12-arg 보조 생성자를 둔다)
 */
data class AdminCompanyVerificationDetailView(
	val id: Long,
	val userId: Long,
	val nickname: String?,
	val email: String?,
	val status: CompanyImageVerificationStatus,
	val createdAt: LocalDateTime?,
	val imageKey: String,
	/** 제출 시점에 스냅샷한 이전(기존 프로필) 회사명. (company_image_verifications.previous_company_name) */
	val previousCompanyName: String?,
	val companyEmail: String?,
	val job: String?,
	/** 유저가 제출 시 기입한 희망 회사명. (company_image_verifications.company_name) */
	val requestedCompanyName: String?,
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
		status: CompanyImageVerificationStatus,
		createdAt: LocalDateTime?,
		imageKey: String,
		previousCompanyName: String?,
		companyEmail: String?,
		job: String?,
		requestedCompanyName: String?,
		rejectionReason: String?,
	) : this(id, userId, nickname, email, status, createdAt, imageKey, previousCompanyName, companyEmail, job, requestedCompanyName, rejectionReason, null)
}
