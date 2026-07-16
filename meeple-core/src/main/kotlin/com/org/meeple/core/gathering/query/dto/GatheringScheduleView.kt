package com.org.meeple.core.gathering.query.dto

import com.org.meeple.common.gathering.GatheringProductType
import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.common.user.Gender
import java.time.LocalDateTime

/**
 * 유저용 모임 상세에 포함되는 일정 한 건(read model). 한 모임의 일정 목록으로 노출된다.
 * 시간 범위는 [startAt](필수)·[endAt](선택)으로, 진행 상태는 [status]로 표현한다.
 * 가격은 gathering_products에 저장된 성별·티어별 확정 금액([products])으로 가진다(율 계산 없음).
 * 얼리버드 선착순 수량([earlyBirdCapacity]·[earlyBirdRemaining])은 남/녀 공유 카운터라 일정이 가진다.
 * 남/녀 여분([maleRemaining]·[femaleRemaining])은 해당 성별 소진 판정([soldOutFor])에 쓴다.
 * 금액 티어 계산은 offline 상세 응답과 결제 체크아웃 응답이 공유하므로 이 read model에 캡슐화한다.
 */
data class GatheringScheduleView(
	val id: Long,
	val startAt: LocalDateTime,
	val endAt: LocalDateTime?,
	val maleRemaining: Int,
	val femaleRemaining: Int,
	val earlyBirdCapacity: Int?,
	val earlyBirdRemaining: Int?,
	val status: GatheringScheduleStatus,
	val products: List<GatheringProductView>,
) {

	/** 얼리버드 소진 여부. 티어가 있고([earlyBirdRemaining] non-null) 남은 개수가 0 이하일 때만 true. */
	val earlyBirdSoldOut: Boolean
		get() = earlyBirdRemaining != null && earlyBirdRemaining <= 0

	/** [gender] 성별의 정가. */
	fun feeFor(gender: Gender): Int =
		checkNotNull(priceFor(gender, GatheringProductType.NORMAL)) { "정가 상품이 없습니다: $id" }

	/** [gender] 성별의 할인가(얼리버드 소진 시 적용 대상). 없으면 null. */
	fun discountFeeFor(gender: Gender): Int? =
		priceFor(gender, GatheringProductType.DISCOUNT)

	/** [gender] 성별 정원 소진 여부. */
	fun soldOutFor(gender: Gender): Boolean =
		(if (gender == Gender.MALE) maleRemaining else femaleRemaining) <= 0

	/** [gender] 성별의 얼리버드가(저장 금액). 얼리버드 티어가 존재하고 미소진일 때만 반환하고, 없거나 소진이면 null. */
	fun earlyBirdFeeFor(gender: Gender): Int? {
		if (earlyBirdRemaining == null || earlyBirdSoldOut) return null
		return priceFor(gender, GatheringProductType.EARLY_BIRD)
	}

	/** [gender] 성별의 서버 확정 실결제가: 얼리버드 유효 → 얼리버드가, 소진 & 할인가 존재 → 할인가, 그 외 → 정가. */
	fun salePriceFor(gender: Gender): Int =
		earlyBirdFeeFor(gender)
			?: (if (earlyBirdSoldOut) discountFeeFor(gender) else null)
			?: feeFor(gender)

	private fun priceFor(gender: Gender, type: GatheringProductType): Int? =
		products.firstOrNull { product: GatheringProductView -> product.gender == gender && product.type == type }?.price
}
