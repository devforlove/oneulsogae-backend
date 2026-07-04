package com.org.meeple.api.admin.response

import com.org.meeple.admin.report.query.dto.AdminReportDetailView
import java.time.LocalDateTime

/** 어드민 신고 상세 응답. 목록 필드 + 신고 사유·채팅방. enum은 코드+한글 라벨을 함께 노출한다. */
data class AdminReportDetailResponse(
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
	val description: String?,
	val chatRoomId: Long?,
) {
	companion object {
		fun of(view: AdminReportDetailView): AdminReportDetailResponse =
			AdminReportDetailResponse(
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
				description = view.description,
				chatRoomId = view.chatRoomId,
			)
	}
}
