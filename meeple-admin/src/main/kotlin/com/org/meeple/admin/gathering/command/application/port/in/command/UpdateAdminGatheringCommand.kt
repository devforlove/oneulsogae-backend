package com.org.meeple.admin.gathering.command.application.port.`in`.command

import com.org.meeple.common.gathering.GatheringType
import java.time.LocalDateTime

/**
 * 어드민 모임 전체 수정 입력. 전 필드를 새 값으로 교체한다.
 * 대표 이미지는 원시 바이트·메타로 받는다: [imageContent]가 있으면 교체하고, null이면 기존 이미지를 유지한다.
 */
data class UpdateAdminGatheringCommand(
	val type: GatheringType,
	val title: String,
	val description: String?,
	val imageContent: ByteArray?,
	val imageContentType: String?,
	val imageSize: Long,
	val region: String,
	val gatheringAt: LocalDateTime,
	val minParticipants: Int,
	val maxParticipants: Int,
	// 정상가(남/녀, 필수)
	val maleFee: Int,
	val femaleFee: Int,
	// 얼리버드 특가(남/녀, 선택) — 가격이 있으면 적용 인원(earlyBirdCapacity)도 함께
	val earlyBirdMaleFee: Int?,
	val earlyBirdFemaleFee: Int?,
	val earlyBirdCapacity: Int?,
	// 할인가(남/녀, 선택)
	val discountMaleFee: Int?,
	val discountFemaleFee: Int?,
)
