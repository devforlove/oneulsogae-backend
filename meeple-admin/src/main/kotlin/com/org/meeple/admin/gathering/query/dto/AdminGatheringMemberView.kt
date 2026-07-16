package com.org.meeple.admin.gathering.query.dto

import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.user.Gender
import java.time.LocalDateTime

/**
 * 어드민 일정별 참가 신청 목록의 한 행(read model).
 * [amount]는 (schedule, user)의 최신 결제 기록 금액(기록이 없으면 null — 픽스처 등 예외 상황).
 */
data class AdminGatheringMemberView(
	val memberId: Long,
	val userId: Long,
	val nickname: String?,
	val gender: Gender,
	val status: GatheringMemberStatus,
	val amount: Int?,
	val appliedAt: LocalDateTime,
)
