package com.org.meeple.api.admin.response

import com.org.meeple.admin.notice.query.dto.AdminNoticePage

/** 어드민 공지 목록 페이지 응답. (offset 페이징) */
data class AdminNoticePageResponse(
	val content: List<AdminNoticeResponse>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
	val hasNext: Boolean,
) {
	companion object {
		fun of(page: AdminNoticePage): AdminNoticePageResponse =
			AdminNoticePageResponse(
				content = AdminNoticeResponse.listOf(page.content),
				page = page.page,
				size = page.size,
				totalElements = page.totalElements,
				totalPages = page.totalPages,
				hasNext = page.hasNext,
			)
	}
}
