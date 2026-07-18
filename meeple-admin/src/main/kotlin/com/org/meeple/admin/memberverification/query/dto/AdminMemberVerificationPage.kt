package com.org.meeple.admin.memberverification.query.dto

/**
 * 어드민 멤버 인증 목록 한 페이지(read model). offset(page·size) 페이징 결과.
 * [totalElements]는 (status 필터 반영) 전체 개수, [totalPages]/[hasNext]는 파생값.
 */
data class AdminMemberVerificationPage(
	val content: AdminMemberVerificationViews,
	val page: Int,
	val size: Int,
	val totalElements: Long,
) {
	val totalPages: Int
		get() = if (size <= 0) 0 else ((totalElements + size - 1) / size).toInt()

	val hasNext: Boolean
		get() = (page + 1).toLong() * size < totalElements

	companion object {
		fun empty(page: Int, size: Int): AdminMemberVerificationPage =
			AdminMemberVerificationPage(AdminMemberVerificationViews.empty(), page, size, 0)
	}
}
