package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringPage

/** 어드민 모임 목록 페이지 응답. (offset 페이징) */
data class AdminGatheringPageResponse(
	val content: List<AdminGatheringResponse>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
	val hasNext: Boolean,
) {
	companion object {
		fun of(page: AdminGatheringPage): AdminGatheringPageResponse =
			AdminGatheringPageResponse(
				content = AdminGatheringResponse.listOf(page.content),
				page = page.page,
				size = page.size,
				totalElements = page.totalElements,
				totalPages = page.totalPages,
				hasNext = page.hasNext,
			)
	}
}
