package com.org.meeple.admin.gathering.query.dto

import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.user.Gender
import java.time.LocalDateTime

/**
 * 어드민 참가 신청 목록의 한 행(read model).
 * [amount]는 (schedule, user)의 최신 결제 기록 금액(기록이 없으면 null — 픽스처 등 예외 상황).
 * [scheduleId]·[gatheringTitle]·[scheduleStartAt]은 전역(모임 무관) 목록에서 어느 모임·일정의 신청인지 알기 위한 맥락이다.
 * [memberVerified]는 회원 인증(gathering_profile) 완료 여부다(승인 가능 여부 표시용).
 * [memberVerificationId]는 유저의 최신 멤버 인증 제출 id다(제출 이력이 없으면 null). 어드민 프론트가 멤버 인증 상세로 바로 가는 데 쓴다.
 */
data class AdminGatheringMemberView(
	val memberId: Long,
	val userId: Long,
	val nickname: String?,
	val gender: Gender,
	val status: GatheringMemberStatus,
	val amount: Int?,
	val appliedAt: LocalDateTime,
	val scheduleId: Long,
	val gatheringTitle: String,
	val scheduleStartAt: LocalDateTime,
	val memberVerified: Boolean,
	val memberVerificationId: Long?,
)
