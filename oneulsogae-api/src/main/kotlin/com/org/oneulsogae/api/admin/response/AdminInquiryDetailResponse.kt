package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.inquiry.query.dto.AdminInquiryDetailView
import com.org.oneulsogae.common.inquiry.InquiryCategory
import com.org.oneulsogae.common.inquiry.InquiryStatus
import java.time.LocalDateTime

/** 어드민 문의 상세 응답. 목록 필드 + 본문(message)·답변(answer/answeredAt)·작성자(userId). */
data class AdminInquiryDetailResponse(
	val id: Long,
	val userId: Long?,
	val category: InquiryCategory,
	val email: String,
	val message: String,
	val status: InquiryStatus,
	val answer: String?,
	val answeredAt: LocalDateTime?,
	val createdAt: LocalDateTime?,
) {
	companion object {
		fun of(view: AdminInquiryDetailView): AdminInquiryDetailResponse =
			AdminInquiryDetailResponse(
				id = view.id,
				userId = view.userId,
				category = view.category,
				email = view.email,
				message = view.message,
				status = view.status,
				answer = view.answer,
				answeredAt = view.answeredAt,
				createdAt = view.createdAt,
			)
	}
}
