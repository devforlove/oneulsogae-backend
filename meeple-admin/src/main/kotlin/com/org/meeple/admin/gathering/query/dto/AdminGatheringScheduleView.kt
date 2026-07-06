package com.org.meeple.admin.gathering.query.dto

import com.org.meeple.common.gathering.GatheringScheduleStatus
import java.time.LocalDateTime

/**
 * 어드민 모임 상세에 포함되는 일정 한 건(read model). 한 모임의 일정 목록으로 노출된다.
 * 시간 범위는 [startAt](필수)·[endAt](선택)으로, 진행 상태는 [status]로 표현한다.
 * 참가비는 일정별로 가진다: 정상가([maleFee]·[femaleFee], 필수), 얼리버드([earlyBirdMaleFee]·[earlyBirdFemaleFee]·[earlyBirdCapacity]·남은 개수[earlyBirdRemaining], 선택),
 * 할인가([discountMaleFee]·[discountFemaleFee], 선택). 없는 티어는 null.
 */
data class AdminGatheringScheduleView(
	val id: Long,
	val startAt: LocalDateTime,
	val endAt: LocalDateTime?,
	val maleFee: Int,
	val femaleFee: Int,
	val earlyBirdMaleFee: Int?,
	val earlyBirdFemaleFee: Int?,
	val earlyBirdCapacity: Int?,
	val earlyBirdRemaining: Int?,
	val discountMaleFee: Int?,
	val discountFemaleFee: Int?,
	val status: GatheringScheduleStatus,
)
