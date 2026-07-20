package com.org.oneulsogae.admin.gathering.query.dto

import com.org.oneulsogae.common.gathering.GatheringScheduleStatus
import java.time.LocalDateTime

/**
 * 어드민 모임 상세에 포함되는 일정 한 건(read model). 한 모임의 일정 목록으로 노출된다.
 * 시간 범위는 [startAt](필수)·[endAt](선택)으로, 진행 상태는 [status]로 표현한다.
 * 가격은 gathering_products의 저장 금액으로 노출한다: 정가([maleFee]·[femaleFee], 필수),
 * 얼리버드가([earlyBirdMaleFee]·[earlyBirdFemaleFee], 선택 — 할인율이 아니라 확정 금액),
 * 할인가([discountMaleFee]·[discountFemaleFee], 선택). 없는 티어는 null.
 * 얼리버드 선착순 수량([earlyBirdCapacity]·[earlyBirdRemaining])은 일정 테이블 컬럼이다.
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
