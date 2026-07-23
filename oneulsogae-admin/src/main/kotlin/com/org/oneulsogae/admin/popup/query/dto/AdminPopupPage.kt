package com.org.oneulsogae.admin.popup.query.dto

/**
 * 어드민 팝업 목록 한 페이지(read model). offset(page·size) 페이징 결과.
 * [totalElements]는 (soft delete·개인 팝업 제외) 전역 팝업 전체 개수, [totalPages]/[hasNext]는 파생값.
 */
data class AdminPopupPage(
	val content: AdminPopupViews,
	val page: Int,
	val size: Int,
	val totalElements: Long,
) {
	val totalPages: Int
		get() = if (size <= 0) 0 else ((totalElements + size - 1) / size).toInt()

	val hasNext: Boolean
		get() = (page + 1).toLong() * size < totalElements
}
