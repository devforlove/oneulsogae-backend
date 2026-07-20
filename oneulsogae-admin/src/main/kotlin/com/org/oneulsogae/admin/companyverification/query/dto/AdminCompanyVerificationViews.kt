package com.org.oneulsogae.admin.companyverification.query.dto

/** 어드민 회사 이미지 인증 목록 read model 일급 컬렉션. */
data class AdminCompanyVerificationViews(
	val values: List<AdminCompanyVerificationView>,
) {
	companion object {
		fun empty(): AdminCompanyVerificationViews = AdminCompanyVerificationViews(emptyList())
	}
}
