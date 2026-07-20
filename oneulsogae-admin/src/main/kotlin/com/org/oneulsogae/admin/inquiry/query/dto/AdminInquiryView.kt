package com.org.oneulsogae.admin.inquiry.query.dto

import com.org.oneulsogae.common.inquiry.InquiryCategory
import com.org.oneulsogae.common.inquiry.InquiryStatus
import java.time.LocalDateTime

/** 어드민 문의 목록 한 건(read model). 본문(message)은 상세에서만 노출한다. */
data class AdminInquiryView(
	val id: Long,
	val category: InquiryCategory,
	val status: InquiryStatus,
	val email: String,
	val createdAt: LocalDateTime?,
)
