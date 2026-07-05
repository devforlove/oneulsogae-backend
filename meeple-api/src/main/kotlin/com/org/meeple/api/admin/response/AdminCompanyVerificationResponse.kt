package com.org.meeple.api.admin.response

import com.org.meeple.admin.companyverification.query.dto.AdminCompanyVerificationView
import java.time.LocalDateTime

/**
 * 어드민 회사 이미지 인증 목록 항목 응답. status는 코드(name)와 한글 라벨(description)을 함께 노출한다.
 * 서류는 오브젝트 키 대신 열람용 presigned URL([imageUrl])만 노출한다.
 */
data class AdminCompanyVerificationResponse(
	val id: Long,
	val userId: Long,
	val status: String,
	val statusLabel: String,
	val createdAt: LocalDateTime?,
	val nickname: String?,
	val email: String?,
	val imageUrl: String?,
) {
	companion object {
		fun of(view: AdminCompanyVerificationView): AdminCompanyVerificationResponse =
			AdminCompanyVerificationResponse(
				id = view.id,
				userId = view.userId,
				status = view.status.name,
				statusLabel = view.status.description,
				createdAt = view.createdAt,
				nickname = view.nickname,
				email = view.email,
				imageUrl = view.imageUrl,
			)
	}
}
