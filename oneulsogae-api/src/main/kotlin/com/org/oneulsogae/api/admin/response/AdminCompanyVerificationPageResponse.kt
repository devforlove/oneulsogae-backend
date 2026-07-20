package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.companyverification.query.dto.AdminCompanyVerificationPage

/** 어드민 회사 이미지 인증 목록 페이지 응답. (offset 페이징) */
data class AdminCompanyVerificationPageResponse(
	val content: List<AdminCompanyVerificationResponse>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
	val hasNext: Boolean,
) {
	companion object {
		fun of(page: AdminCompanyVerificationPage): AdminCompanyVerificationPageResponse =
			AdminCompanyVerificationPageResponse(
				content = page.content.values.map(AdminCompanyVerificationResponse::of),
				page = page.page,
				size = page.size,
				totalElements = page.totalElements,
				totalPages = page.totalPages,
				hasNext = page.hasNext,
			)
	}
}
