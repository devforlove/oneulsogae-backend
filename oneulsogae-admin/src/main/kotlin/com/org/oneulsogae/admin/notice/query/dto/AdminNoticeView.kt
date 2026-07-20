package com.org.oneulsogae.admin.notice.query.dto

import java.time.LocalDateTime

/** 어드민 공지 목록 한 건(read model). 본문(description)은 상세에서만 노출한다. */
data class AdminNoticeView(
	val id: Long,
	val title: String,
	val createdAt: LocalDateTime?,
)
