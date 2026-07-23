package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.popup.query.dto.AdminPopupPage

/** 어드민 팝업 목록 페이지 응답. (offset 페이징) */
data class AdminPopupPageResponse(
	val content: List<AdminPopupResponse>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
	val hasNext: Boolean,
) {
	companion object {
		fun of(page: AdminPopupPage): AdminPopupPageResponse =
			AdminPopupPageResponse(
				content = AdminPopupResponse.listOf(page.content),
				page = page.page,
				size = page.size,
				totalElements = page.totalElements,
				totalPages = page.totalPages,
				hasNext = page.hasNext,
			)
	}
}
