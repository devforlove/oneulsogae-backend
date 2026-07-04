package com.org.meeple.core.report.query.dto

import com.org.meeple.common.report.ReportStatus
import com.org.meeple.common.report.ReportType
import java.time.LocalDateTime

/**
 * 어드민 신고 목록 한 건(read model). 유저 신고(toUserId 존재)만 대상으로 하며,
 * 신고자·대상의 표시 정보(닉네임·이메일)를 users·user_details 조인으로 채운다. (없으면 null)
 */
data class AdminReportSummaryView(
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
)
