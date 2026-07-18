package com.org.meeple.api.admin.response

import com.org.meeple.admin.memberverification.query.dto.AdminMemberVerificationView
import java.time.LocalDateTime

/**
 * 어드민 멤버 인증 목록 항목 응답. status는 코드(name)와 한글 라벨(description)을 함께 노출한다.
 * 사진 3종 열람 URL은 상세 응답에서만 내려준다.
 */
data class AdminMemberVerificationResponse(
	val id: Long,
	val userId: Long,
	val status: String,
	val statusLabel: String,
	val jobCategory: String,
	val createdAt: LocalDateTime?,
	val nickname: String?,
	val email: String?,
) {
	companion object {
		fun of(view: AdminMemberVerificationView): AdminMemberVerificationResponse =
			AdminMemberVerificationResponse(
				id = view.id,
				userId = view.userId,
				status = view.status.name,
				statusLabel = view.status.description,
				jobCategory = view.jobCategory,
				createdAt = view.createdAt,
				nickname = view.nickname,
				email = view.email,
			)
	}
}
