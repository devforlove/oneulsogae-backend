package com.org.meeple.admin.memberverification.query.dto

/** 어드민 멤버 인증 목록 read model 일급 컬렉션. */
data class AdminMemberVerificationViews(
	val values: List<AdminMemberVerificationView>,
) {
	companion object {
		fun empty(): AdminMemberVerificationViews = AdminMemberVerificationViews(emptyList())
	}
}
