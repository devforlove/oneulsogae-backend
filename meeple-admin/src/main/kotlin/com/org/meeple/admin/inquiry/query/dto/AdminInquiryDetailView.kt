package com.org.meeple.admin.inquiry.query.dto

import com.org.meeple.common.inquiry.InquiryCategory
import com.org.meeple.common.inquiry.InquiryStatus
import java.time.LocalDateTime

/** 어드민 문의 상세 read model. 목록 필드 + 본문(message)·답변(answer/answeredAt)·작성자(userId). */
data class AdminInquiryDetailView(
	val id: Long,
	val userId: Long?,
	val category: InquiryCategory,
	val email: String,
	val message: String,
	val status: InquiryStatus,
	val answer: String?,
	val answeredAt: LocalDateTime?,
	val createdAt: LocalDateTime?,
)
