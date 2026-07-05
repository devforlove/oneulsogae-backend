package com.org.meeple.api.admin.response

import com.org.meeple.admin.inquiry.query.dto.AdminInquiryView
import com.org.meeple.admin.inquiry.query.dto.AdminInquiryViews
import com.org.meeple.common.inquiry.InquiryCategory
import com.org.meeple.common.inquiry.InquiryStatus
import java.time.LocalDateTime

/** 어드민 문의 목록 항목 응답. 본문(message)은 상세에서만 노출한다. */
data class AdminInquiryResponse(
	val id: Long,
	val category: InquiryCategory,
	val status: InquiryStatus,
	val email: String,
	val createdAt: LocalDateTime?,
) {
	companion object {
		private fun of(view: AdminInquiryView): AdminInquiryResponse =
			AdminInquiryResponse(
				id = view.id,
				category = view.category,
				status = view.status,
				email = view.email,
				createdAt = view.createdAt,
			)

		fun listOf(views: AdminInquiryViews): List<AdminInquiryResponse> =
			views.values.map { view: AdminInquiryView -> of(view) }
	}
}
