package com.org.meeple.api.admin.response

import com.org.meeple.admin.companyverification.query.dto.AdminCompanyVerificationDetailView
import java.time.LocalDateTime

/**
 * 어드민 회사 이미지 인증 상세 응답. 목록 필드 + 사용자가 주장한 직장 정보(companyName·companyEmail·job).
 * status는 코드(name)와 한글 라벨(description)을 함께 노출하고, 서류는 열람용 presigned URL([imageUrl])만 노출한다.
 */
data class AdminCompanyVerificationDetailResponse(
	val id: Long,
	val userId: Long,
	val status: String,
	val statusLabel: String,
	val createdAt: LocalDateTime?,
	val nickname: String?,
	val email: String?,
	val companyName: String?,
	val companyEmail: String?,
	val job: String?,
	val imageUrl: String?,
) {
	companion object {
		fun of(view: AdminCompanyVerificationDetailView): AdminCompanyVerificationDetailResponse =
			AdminCompanyVerificationDetailResponse(
				id = view.id,
				userId = view.userId,
				status = view.status.name,
				statusLabel = view.status.description,
				createdAt = view.createdAt,
				nickname = view.nickname,
				email = view.email,
				companyName = view.companyName,
				companyEmail = view.companyEmail,
				job = view.job,
				imageUrl = view.imageUrl,
			)
	}
}
