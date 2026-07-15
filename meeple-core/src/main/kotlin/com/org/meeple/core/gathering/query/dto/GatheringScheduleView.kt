package com.org.meeple.core.gathering.query.dto

import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.common.user.Gender
import java.time.LocalDateTime

/**
 * 유저용 모임 상세에 포함되는 일정 한 건(read model). 한 모임의 일정 목록으로 노출된다.
 * 시간 범위는 [startAt](필수)·[endAt](선택)으로, 진행 상태는 [status]로 표현한다.
 * 참가비는 일정별로 가진다: 정상가([maleFee]·[femaleFee], 필수), 얼리버드(할인율[earlyBirdDiscountRate]·[earlyBirdCapacity]·남은 개수[earlyBirdRemaining], 선택),
 * 할인가([discountMaleFee]·[discountFemaleFee], 선택). 없는 티어는 null.
 * 얼리버드 금액은 저장하지 않고 할인율(%)만 가지며, [earlyBirdFeeFor]가 정상가에 곱해 계산한다.
 * 남/녀 여분([maleRemaining]·[femaleRemaining])은 해당 성별 소진 판정([soldOutFor])에 쓴다.
 * 금액 티어 계산은 offline 상세 응답과 결제 체크아웃 응답이 공유하므로 이 read model에 캡슐화한다.
 */
data class GatheringScheduleView(
	val id: Long,
	val startAt: LocalDateTime,
	val endAt: LocalDateTime?,
	val maleFee: Int,
	val femaleFee: Int,
	val maleRemaining: Int,
	val femaleRemaining: Int,
	val earlyBirdDiscountRate: Int?,
	val earlyBirdCapacity: Int?,
	val earlyBirdRemaining: Int?,
	val discountMaleFee: Int?,
	val discountFemaleFee: Int?,
	val status: GatheringScheduleStatus,
) {

	/** 얼리버드 소진 여부. 티어가 있고([earlyBirdRemaining] non-null) 남은 개수가 0 이하일 때만 true. */
	val earlyBirdSoldOut: Boolean
		get() = earlyBirdRemaining != null && earlyBirdRemaining <= 0

	/** [gender] 성별의 정가. */
	fun feeFor(gender: Gender): Int =
		if (gender == Gender.MALE) maleFee else femaleFee

	/** [gender] 성별의 할인가(얼리버드 소진 시 적용 대상). 없으면 null. */
	fun discountFeeFor(gender: Gender): Int? =
		if (gender == Gender.MALE) discountMaleFee else discountFemaleFee

	/** [gender] 성별 정원 소진 여부. */
	fun soldOutFor(gender: Gender): Boolean =
		(if (gender == Gender.MALE) maleRemaining else femaleRemaining) <= 0

	/**
	 * [gender] 성별의 얼리버드가. 얼리버드 티어가 존재하고 미소진일 때만 정상가 × (100 - 할인율) / 100(버림)을 반환한다.
	 * 티어가 없거나([earlyBirdRemaining] null) 소진이면 null.
	 */
	fun earlyBirdFeeFor(gender: Gender): Int? {
		if (earlyBirdRemaining == null || earlyBirdSoldOut) return null
		return earlyBirdDiscountRate?.let { rate: Int -> feeFor(gender) * (100 - rate) / 100 }
	}

	/** [gender] 성별의 서버 확정 실결제가: 얼리버드 유효 → 얼리버드가, 소진 & 할인가 존재 → 할인가, 그 외 → 정가. */
	fun salePriceFor(gender: Gender): Int =
		earlyBirdFeeFor(gender)
			?: (if (earlyBirdSoldOut) discountFeeFor(gender) else null)
			?: feeFor(gender)
}
