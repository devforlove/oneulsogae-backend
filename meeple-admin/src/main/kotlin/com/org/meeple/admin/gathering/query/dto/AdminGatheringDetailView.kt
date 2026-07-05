package com.org.meeple.admin.gathering.query.dto

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import java.time.LocalDateTime

/**
 * 어드민 모임 상세 read model. 목록 필드 + 소개·참가비 상세.
 * 참가비는 성별·티어별 flat 필드로 투영한다(얼리버드·할인가는 없는 모임이면 null).
 * (조회 전용이라 command 도메인 값 객체를 참조하지 않고 자체 flat read model로 둔다)
 */
data class AdminGatheringDetailView(
	val id: Long,
	val type: GatheringType,
	val title: String,
	val description: String?,
	val region: String,
	val gatheringAt: LocalDateTime,
	val capacity: Int,
	val maleFee: Int,
	val femaleFee: Int,
	val earlyBirdMaleFee: Int?,
	val earlyBirdFemaleFee: Int?,
	val discountMaleFee: Int?,
	val discountFemaleFee: Int?,
	val status: GatheringStatus,
	val createdAt: LocalDateTime?,
)
