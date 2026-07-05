package com.org.meeple.api.admin.response

import com.org.meeple.admin.inquiry.query.dto.AdminInquiryPage

/** 어드민 문의 목록 페이지 응답. (offset 페이징) */
data class AdminInquiryPageResponse(
	val content: List<AdminInquiryResponse>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
	val hasNext: Boolean,
) {
	companion object {
		fun of(page: AdminInquiryPage): AdminInquiryPageResponse =
			AdminInquiryPageResponse(
				content = AdminInquiryResponse.listOf(page.content),
				page = page.page,
				size = page.size,
				totalElements = page.totalElements,
				totalPages = page.totalPages,
				hasNext = page.hasNext,
			)
	}
}
