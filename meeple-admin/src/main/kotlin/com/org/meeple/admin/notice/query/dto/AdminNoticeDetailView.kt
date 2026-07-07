package com.org.meeple.admin.notice.query.dto

import java.time.LocalDateTime

/** 어드민 공지 상세 read model. 목록 필드 + 본문(description). */
data class AdminNoticeDetailView(
	val id: Long,
	val title: String,
	val description: String,
	val createdAt: LocalDateTime?,
)
