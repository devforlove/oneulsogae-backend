package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.report.query.dto.AdminReportSummaryView
import java.time.LocalDateTime

/** 어드민 신고 목록 항목 응답. enum은 코드(name)와 한글 라벨(description)을 함께 노출한다. */
data class AdminReportSummaryResponse(
	val id: Long,
	val type: String,
	val typeLabel: String,
	val status: String,
	val statusLabel: String,
	val createdAt: LocalDateTime?,
	val reporterId: Long,
	val reporterNickname: String?,
	val reporterEmail: String?,
	val targetUserId: Long,
	val targetNickname: String?,
	val targetEmail: String?,
) {
	companion object {
		fun of(view: AdminReportSummaryView): AdminReportSummaryResponse =
			AdminReportSummaryResponse(
				id = view.id,
				type = view.type.name,
				typeLabel = view.type.description,
				status = view.status.name,
				statusLabel = view.status.description,
				createdAt = view.createdAt,
				reporterId = view.reporterId,
				reporterNickname = view.reporterNickname,
				reporterEmail = view.reporterEmail,
				targetUserId = view.targetUserId,
				targetNickname = view.targetNickname,
				targetEmail = view.targetEmail,
			)
	}
}
