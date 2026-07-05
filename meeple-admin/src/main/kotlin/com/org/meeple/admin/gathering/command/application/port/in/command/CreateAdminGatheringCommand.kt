package com.org.meeple.admin.gathering.command.application.port.`in`.command

import com.org.meeple.common.gathering.GatheringType
import java.time.LocalDateTime

/** 어드민 모임 생성 입력. (운영 생성이므로 생성자 userId는 없음 — 저장 시 null) */
data class CreateAdminGatheringCommand(
	val type: GatheringType,
	val title: String,
	val description: String?,
	val region: String,
	val gatheringAt: LocalDateTime,
	val capacity: Int,
	// 정상가(남/녀, 필수)
	val maleFee: Int,
	val femaleFee: Int,
	// 얼리버드 특가(남/녀, 선택)
	val earlyBirdMaleFee: Int?,
	val earlyBirdFemaleFee: Int?,
	// 할인가(남/녀, 선택)
	val discountMaleFee: Int?,
	val discountFemaleFee: Int?,
)
