package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.universityverification.query.dto.AdminUniversityVerificationDetailView
import java.time.LocalDateTime

/**
 * 어드민 학교 이미지 인증 상세 응답. 목록 필드 + 이전 학교명(previousUniversityName) + 사용자가 주장한 학교 정보(universityEmail).
 * status는 코드(name)와 한글 라벨(description)을 함께 노출하고, 서류는 열람용 presigned URL([imageUrl])만 노출한다.
 */
data class AdminUniversityVerificationDetailResponse(
	val id: Long,
	val userId: Long,
	val status: String,
	val statusLabel: String,
	val createdAt: LocalDateTime?,
	val nickname: String?,
	val email: String?,
	val previousUniversityName: String?,
	val universityEmail: String?,
	val requestedUniversityName: String?,
	val rejectionReason: String?,
	val imageUrl: String?,
) {
	companion object {
		fun of(view: AdminUniversityVerificationDetailView): AdminUniversityVerificationDetailResponse =
			AdminUniversityVerificationDetailResponse(
				id = view.id,
				userId = view.userId,
				status = view.status.name,
				statusLabel = view.status.description,
				createdAt = view.createdAt,
				nickname = view.nickname,
				email = view.email,
				previousUniversityName = view.previousUniversityName,
				universityEmail = view.universityEmail,
				requestedUniversityName = view.requestedUniversityName,
				rejectionReason = view.rejectionReason,
				imageUrl = view.imageUrl,
			)
	}
}
