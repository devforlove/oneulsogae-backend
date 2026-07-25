package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.universityverification.query.dto.AdminUniversityVerificationPage

/** 어드민 학교 이미지 인증 목록 페이지 응답. (offset 페이징) */
data class AdminUniversityVerificationPageResponse(
	val content: List<AdminUniversityVerificationResponse>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
	val hasNext: Boolean,
) {
	companion object {
		fun of(page: AdminUniversityVerificationPage): AdminUniversityVerificationPageResponse =
			AdminUniversityVerificationPageResponse(
				content = page.content.values.map(AdminUniversityVerificationResponse::of),
				page = page.page,
				size = page.size,
				totalElements = page.totalElements,
				totalPages = page.totalPages,
				hasNext = page.hasNext,
			)
	}
}
