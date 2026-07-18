package com.org.meeple.api.admin.response

import com.org.meeple.admin.memberverification.query.dto.AdminMemberVerificationDetailView
import java.time.LocalDateTime

/**
 * 어드민 멤버 인증 상세 응답. 목록 필드 + 직업 상세(jobDetail)·반려 사유(rejectionReason) +
 * 사진 3종(얼굴·신분증·서류)의 열람용 presigned URL.
 * status는 코드(name)와 한글 라벨(description)을 함께 노출하고, 사진은 오브젝트 키 대신 URL만 노출한다.
 */
data class AdminMemberVerificationDetailResponse(
	val id: Long,
	val userId: Long,
	val status: String,
	val statusLabel: String,
	val jobCategory: String,
	val jobDetail: String,
	val rejectionReason: String?,
	val createdAt: LocalDateTime?,
	val nickname: String?,
	val email: String?,
	val faceImageUrl: String?,
	val idCardImageUrl: String?,
	val documentImageUrl: String?,
) {
	companion object {
		fun of(view: AdminMemberVerificationDetailView): AdminMemberVerificationDetailResponse =
			AdminMemberVerificationDetailResponse(
				id = view.id,
				userId = view.userId,
				status = view.status.name,
				statusLabel = view.status.description,
				jobCategory = view.jobCategory,
				jobDetail = view.jobDetail,
				rejectionReason = view.rejectionReason,
				createdAt = view.createdAt,
				nickname = view.nickname,
				email = view.email,
				faceImageUrl = view.faceImageUrl,
				idCardImageUrl = view.idCardImageUrl,
				documentImageUrl = view.documentImageUrl,
			)
	}
}
