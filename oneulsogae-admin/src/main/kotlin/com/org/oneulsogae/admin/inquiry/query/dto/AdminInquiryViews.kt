package com.org.oneulsogae.admin.inquiry.query.dto

/** 어드민 문의 목록 read model 일급 컬렉션. */
data class AdminInquiryViews(
	val values: List<AdminInquiryView>,
) {
	companion object {
		fun empty(): AdminInquiryViews = AdminInquiryViews(emptyList())
	}
}
