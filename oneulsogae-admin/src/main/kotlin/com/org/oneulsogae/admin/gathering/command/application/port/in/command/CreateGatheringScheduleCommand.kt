package com.org.oneulsogae.admin.gathering.command.application.port.`in`.command

import java.time.LocalDateTime

/**
 * 모임 일정 생성 입력. [gatheringId] 모임에 [startAt]~[endAt] 시간 범위·참가비의 일정을 추가한다.
 * ([endAt]이 없으면 종료 시각 미정 일정) 참가비는 정상가(남/녀 필수)·얼리버드 할인율(할인율+인원 선택)·할인가(남/녀 선택)로 받는다.
 */
data class CreateGatheringScheduleCommand(
	val gatheringId: Long,
	val startAt: LocalDateTime,
	val endAt: LocalDateTime?,
	// 정상가(남/녀, 필수)
	val maleFee: Int,
	val femaleFee: Int,
	// 얼리버드(할인율%, 선택) — 할인율이 있으면 적용 인원(earlyBirdCapacity)도 함께
	val earlyBirdDiscountRate: Int?,
	val earlyBirdCapacity: Int?,
	// 할인가(남/녀, 선택)
	val discountMaleFee: Int?,
	val discountFemaleFee: Int?,
)
