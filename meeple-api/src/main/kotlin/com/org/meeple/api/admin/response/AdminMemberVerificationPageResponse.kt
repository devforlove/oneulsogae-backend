package com.org.meeple.api.admin.response

import com.org.meeple.admin.memberverification.query.dto.AdminMemberVerificationPage

/** 어드민 멤버 인증 목록 페이지 응답. (offset 페이징) */
data class AdminMemberVerificationPageResponse(
	val content: List<AdminMemberVerificationResponse>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
	val hasNext: Boolean,
) {
	companion object {
		fun of(page: AdminMemberVerificationPage): AdminMemberVerificationPageResponse =
			AdminMemberVerificationPageResponse(
				content = page.content.values.map(AdminMemberVerificationResponse::of),
				page = page.page,
				size = page.size,
				totalElements = page.totalElements,
				totalPages = page.totalPages,
				hasNext = page.hasNext,
			)
	}
}
