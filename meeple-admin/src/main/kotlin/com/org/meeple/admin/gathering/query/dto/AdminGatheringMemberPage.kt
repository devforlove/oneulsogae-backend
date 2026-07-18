package com.org.meeple.admin.gathering.query.dto

/**
 * 어드민 일정별 참가 신청 목록 한 페이지(read model). offset(page·size) 페이징 결과.
 * [totalElements]는 (status 필터 반영) 전체 개수, [totalPages]/[hasNext]는 파생값.
 */
data class AdminGatheringMemberPage(
	val content: AdminGatheringMemberViews,
	val page: Int,
	val size: Int,
	val totalElements: Long,
) {
	val totalPages: Int
		get() = if (size <= 0) 0 else ((totalElements + size - 1) / size).toInt()

	val hasNext: Boolean
		get() = (page + 1).toLong() * size < totalElements

	companion object {
		fun empty(page: Int, size: Int): AdminGatheringMemberPage =
			AdminGatheringMemberPage(AdminGatheringMemberViews(emptyList()), page, size, 0)
	}
}
