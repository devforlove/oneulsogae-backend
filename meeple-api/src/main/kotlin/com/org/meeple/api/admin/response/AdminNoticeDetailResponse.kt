package com.org.meeple.api.admin.response

import com.org.meeple.admin.notice.query.dto.AdminNoticeDetailView
import java.time.LocalDateTime

/** 어드민 공지 상세 응답. 목록 필드 + 본문(description). */
data class AdminNoticeDetailResponse(
	val id: Long,
	val title: String,
	val description: String,
	val createdAt: LocalDateTime?,
) {
	companion object {
		fun of(view: AdminNoticeDetailView): AdminNoticeDetailResponse =
			AdminNoticeDetailResponse(
				id = view.id,
				title = view.title,
				description = view.description,
				createdAt = view.createdAt,
			)
	}
}
