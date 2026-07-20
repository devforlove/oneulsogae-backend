package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeView
import com.org.oneulsogae.admin.notice.query.dto.AdminNoticeViews
import java.time.LocalDateTime

/** 어드민 공지 목록 항목 응답. 본문(description)은 상세에서만 노출한다. */
data class AdminNoticeResponse(
	val id: Long,
	val title: String,
	val createdAt: LocalDateTime?,
) {
	companion object {
		private fun of(view: AdminNoticeView): AdminNoticeResponse =
			AdminNoticeResponse(
				id = view.id,
				title = view.title,
				createdAt = view.createdAt,
			)

		fun listOf(views: AdminNoticeViews): List<AdminNoticeResponse> =
			views.values.map { view: AdminNoticeView -> of(view) }
	}
}
