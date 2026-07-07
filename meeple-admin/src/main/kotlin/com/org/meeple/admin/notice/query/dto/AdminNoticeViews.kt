package com.org.meeple.admin.notice.query.dto

/** 어드민 공지 목록 read model 일급 컬렉션. */
data class AdminNoticeViews(
	val values: List<AdminNoticeView>,
) {
	companion object {
		fun empty(): AdminNoticeViews = AdminNoticeViews(emptyList())
	}
}
