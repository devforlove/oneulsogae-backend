package com.org.oneulsogae.admin.universityverification.query.dto

/** 어드민 학교 이미지 인증 목록 read model 일급 컬렉션. */
data class AdminUniversityVerificationViews(
	val values: List<AdminUniversityVerificationView>,
) {
	companion object {
		fun empty(): AdminUniversityVerificationViews = AdminUniversityVerificationViews(emptyList())
	}
}
