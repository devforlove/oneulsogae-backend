package com.org.meeple.core.gathering.command.application.port.`in`.command

import com.org.meeple.common.gathering.GatheringType
import java.time.LocalDateTime

data class CreateGatheringCommand(
	val userId: Long?,
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
