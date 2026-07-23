package com.org.oneulsogae.admin.popup.query.dto

/** 어드민 팝업 목록 read model 일급 컬렉션. */
data class AdminPopupViews(
	val values: List<AdminPopupView>,
) {
	companion object {
		fun empty(): AdminPopupViews = AdminPopupViews(emptyList())
	}
}
