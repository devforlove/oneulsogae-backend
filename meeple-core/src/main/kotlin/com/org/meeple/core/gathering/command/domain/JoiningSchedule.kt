package com.org.meeple.core.gathering.command.domain

import com.org.meeple.common.gathering.GatheringProductType
import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.gathering.GatheringErrorCode

/**
 * 참가 접수 대상 일정(command 도메인 모델). 접수 규칙(판매 상태·성별 여분 검증)과
 * 확정가 계산(얼리버드 유효 → 얼리버드가, 소진 & 할인가 존재 → 할인가, 그 외 정가), 여분 차감을 캡슐화한다.
 * 가격은 gathering_products에 저장된 성별·티어별 확정 금액을 어댑터가 실어준다(율 계산 없음).
 * 금액 티어 규칙은 query read model(GatheringScheduleView)에도 있지만 CQRS 원칙에 따라 공유하지 않고 각자 구현한다.
 */
class JoiningSchedule(
	val id: Long,
	val gatheringId: Long,
	val status: GatheringScheduleStatus,
	val maleFee: Int,
	val femaleFee: Int,
	var maleRemaining: Int,
	var femaleRemaining: Int,
	var earlyBirdRemaining: Int?,
	val earlyBirdMaleFee: Int?,
	val earlyBirdFemaleFee: Int?,
	val discountMaleFee: Int?,
	val discountFemaleFee: Int?,
) {

	/**
	 * [gender] 성별로 [type] 티어 상품을 접수한다. 결제액은 요청 티어의 저장가로 확정한다
	 * (체크아웃에서 본 상품과 실결제 금액을 일치시킨다).
	 * 얼리버드 티어(EARLY_BIRD)인데 체크아웃 이후 소진됐으면 접수를 거부한다.
	 * 검증 통과 시 해당 성별 여분(얼리버드 티어면 얼리버드 여분도)을 차감한다.
	 */
	fun register(gender: Gender, type: GatheringProductType): JoinPricing {
		validateRegistrable(gender)
		val amount: Int = priceFor(gender, type)
		decrementRemaining(gender)
		val earlyBirdApplied: Boolean = type == GatheringProductType.EARLY_BIRD
		if (earlyBirdApplied) {
			earlyBirdRemaining = checkNotNull(earlyBirdRemaining) - 1
		}
		return JoinPricing(amount = amount, earlyBirdApplied = earlyBirdApplied)
	}

	/** [register]로 차감한 여분을 되돌린다(PG 승인 실패 보상). 성별 여분 +1, [earlyBirdApplied]면 얼리버드 여분도 +1. */
	fun restore(gender: Gender, earlyBirdApplied: Boolean) {
		if (gender == Gender.MALE) maleRemaining += 1 else femaleRemaining += 1
		if (earlyBirdApplied) {
			earlyBirdRemaining = checkNotNull(earlyBirdRemaining) + 1
		}
	}

	// 요청 티어의 저장가. 얼리버드 티어인데 소진됐으면 거부한다.
	private fun priceFor(gender: Gender, type: GatheringProductType): Int =
		when (type) {
			GatheringProductType.EARLY_BIRD -> {
				if (earlyBirdSoldOut()) {
					throw BusinessException(GatheringErrorCode.GATHERING_EARLY_BIRD_SOLD_OUT)
				}
				checkNotNull(earlyBirdFeeFor(gender)) { "얼리버드 상품 가격이 없습니다: $id" }
			}
			GatheringProductType.DISCOUNT -> checkNotNull(discountFeeFor(gender)) { "할인가 상품이 없습니다: $id" }
			GatheringProductType.NORMAL -> feeFor(gender)
		}

	private fun validateRegistrable(gender: Gender) {
		if (status != GatheringScheduleStatus.SCHEDULED) {
			throw BusinessException(GatheringErrorCode.GATHERING_SCHEDULE_NOT_OPEN)
		}
		if (remainingFor(gender) <= 0) {
			throw BusinessException(GatheringErrorCode.GATHERING_SOLD_OUT)
		}
	}

	private fun feeFor(gender: Gender): Int =
		if (gender == Gender.MALE) maleFee else femaleFee

	private fun discountFeeFor(gender: Gender): Int? =
		if (gender == Gender.MALE) discountMaleFee else discountFemaleFee

	private fun remainingFor(gender: Gender): Int =
		if (gender == Gender.MALE) maleRemaining else femaleRemaining

	private fun decrementRemaining(gender: Gender) {
		if (gender == Gender.MALE) maleRemaining -= 1 else femaleRemaining -= 1
	}

	private fun earlyBirdSoldOut(): Boolean {
		val remaining: Int? = earlyBirdRemaining
		return remaining != null && remaining <= 0
	}

	/** 얼리버드 티어가 존재하고 미소진일 때만 해당 성별의 저장된 얼리버드가. 그 외 null. */
	private fun earlyBirdFeeFor(gender: Gender): Int? {
		if (earlyBirdRemaining == null || earlyBirdSoldOut()) return null
		return if (gender == Gender.MALE) earlyBirdMaleFee else earlyBirdFemaleFee
	}
}
