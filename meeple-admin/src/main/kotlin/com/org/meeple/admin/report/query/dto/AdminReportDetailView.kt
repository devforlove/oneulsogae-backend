package com.org.meeple.admin.report.query.dto

import com.org.meeple.common.report.ReportStatus
import com.org.meeple.common.report.ReportType
import java.time.LocalDateTime

/** 어드민 신고 상세 read model. 목록 필드 + 신고 사유(description)·채팅방(chatRoomId). */
data class AdminReportDetailView(
	val id: Long,
	val type: ReportType,
	val status: ReportStatus,
	val createdAt: LocalDateTime?,
	val reporterId: Long,
	val reporterNickname: String?,
	val reporterEmail: String?,
	val targetUserId: Long,
	val targetNickname: String?,
	val targetEmail: String?,
	val description: String?,
	val chatRoomId: Long?,
)
