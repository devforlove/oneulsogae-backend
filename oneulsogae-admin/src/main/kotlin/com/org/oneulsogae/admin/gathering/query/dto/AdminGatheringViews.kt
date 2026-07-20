package com.org.oneulsogae.admin.gathering.query.dto

/** 어드민 모임 목록 read model 일급 컬렉션. */
data class AdminGatheringViews(
	val values: List<AdminGatheringView>,
) {
	companion object {
		fun empty(): AdminGatheringViews = AdminGatheringViews(emptyList())
	}
}
