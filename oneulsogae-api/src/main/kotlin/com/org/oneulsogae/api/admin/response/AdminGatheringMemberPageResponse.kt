package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringMemberPage

/** 어드민 일정별 참가 신청 목록 페이지 응답. (offset 페이징) */
data class AdminGatheringMemberPageResponse(
	val content: List<AdminGatheringMemberResponse>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
	val hasNext: Boolean,
) {
	companion object {
		fun of(page: AdminGatheringMemberPage): AdminGatheringMemberPageResponse =
			AdminGatheringMemberPageResponse(
				content = page.content.values.map(AdminGatheringMemberResponse::of),
				page = page.page,
				size = page.size,
				totalElements = page.totalElements,
				totalPages = page.totalPages,
				hasNext = page.hasNext,
			)
	}
}
