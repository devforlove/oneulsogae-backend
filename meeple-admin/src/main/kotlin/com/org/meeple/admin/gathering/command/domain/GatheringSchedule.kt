package com.org.meeple.admin.gathering.command.domain

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.common.gathering.GatheringScheduleStatus
import java.time.LocalDateTime

/**
 * 한 모임([AdminGathering])에 속한 일정(세션) 하나를 나타내는 도메인 모델(명령 측).
 * 한 모임이 여러 일정을 가질 수 있으므로 gatherings : gathering_schedules = 1 : N이고,
 * 소속 모임은 [gatheringId]로만 참조한다. (영속성은 [com.org.meeple.infra.gathering.command.entity.GatheringScheduleEntity])
 * 시간 범위는 [startAt](필수)·[endAt](선택)으로 표현하고, 생성 시 status는 예정([GatheringScheduleStatus.SCHEDULED])이다.
 * 참가비는 일정별로 가진다: 정상가([fee], 필수), 얼리버드 할인율([earlyBirdDiscountRate]·[earlyBirdCapacity], 선택),
 * 할인가([discountFee], 선택). 얼리버드는 남/녀 금액이 아니라 정상가에 곱해지는 할인율(%)로 저장하고,
 * 할인율이 있으면 적용 인원 수도 함께 가진다. (할인율과 인원은 세트)
 * 정원은 성별로 가진다([maleCapacity]·[femaleCapacity]) — 생성 시 모임 정원(maxParticipants)의 절반으로 정한다.
 * 여분(남은 자리)은 저장 시 정원으로 초기화되므로 도메인은 정원만 다룬다.
 */
data class GatheringSchedule(
	val id: Long = 0,
	val gatheringId: Long,
	val startAt: LocalDateTime,
	val endAt: LocalDateTime?,
	val fee: GatheringFee,
	val maleCapacity: Int,
	val femaleCapacity: Int,
	val earlyBirdDiscountRate: Int?,
	val earlyBirdCapacity: Int?,
	val discountFee: GatheringFee?,
	// 생성 직후는 예정(SCHEDULED). 시작/종료/취소로 전이한다.
	val status: GatheringScheduleStatus = GatheringScheduleStatus.SCHEDULED,
	// 성별·티어별 가격 상품. create()가 구성하며, 저장소에서 로드된 일정은 빈 컬렉션이다(상태 전이 등 products 불필요 경로).
	val products: GatheringProducts = GatheringProducts(emptyList()),
) {

	/**
	 * [target] 상태로 전이한 새 일정을 돌려준다. 전이 규칙:
	 * - SCHEDULED → COMPLETED·CANCELED
	 * - COMPLETED·CANCELED → (종결, 전이 불가)
	 * 불가하면 GATHERING_SCHEDULE_INVALID_STATUS_TRANSITION을 던진다.
	 */
	fun changeStatus(target: GatheringScheduleStatus): GatheringSchedule {
		val allowed: Boolean = when (status) {
			GatheringScheduleStatus.SCHEDULED ->
				target == GatheringScheduleStatus.COMPLETED || target == GatheringScheduleStatus.CANCELED
			GatheringScheduleStatus.COMPLETED, GatheringScheduleStatus.CANCELED -> false
		}
		if (!allowed) {
			throw AdminException(
				AdminErrorCode.GATHERING_SCHEDULE_INVALID_STATUS_TRANSITION,
				"일정 상태를 $status 에서 $target (으)로 전이할 수 없습니다: $id",
			)
		}
		return copy(status = target)
	}

	companion object {

		/**
		 * [gatheringId] 모임에 [startAt]~[endAt] 시간 범위·참가비([fee]·[earlyBirdDiscountRate]·[earlyBirdCapacity]·[discountFee])의 일정을 만든다.
		 * [startAt]은 [now] 이후여야 하고, [endAt]이 있으면 [startAt] 이후여야 한다.
		 * 남/녀 정원은 [maxParticipants](모임 정원)의 절반으로 정한다(정수 나눗셈).
		 * 얼리버드 할인율과 적용 인원은 세트이고, 할인율은 1..100(%), 인원은 1..[maxParticipants] 범위여야 한다. 예정(SCHEDULED)으로 생성한다.
		 * 생성 시 성별·티어별 가격 상품([GatheringProducts])을 함께 구성한다.
		 */
		fun create(
			gatheringId: Long,
			startAt: LocalDateTime,
			endAt: LocalDateTime?,
			fee: GatheringFee,
			earlyBirdDiscountRate: Int?,
			earlyBirdCapacity: Int?,
			discountFee: GatheringFee?,
			maxParticipants: Int,
			now: LocalDateTime,
		): GatheringSchedule {
			if (!startAt.isAfter(now)) {
				throw AdminException(AdminErrorCode.GATHERING_SCHEDULE_INVALID_START_AT)
			}
			if (endAt != null && !endAt.isAfter(startAt)) {
				throw AdminException(AdminErrorCode.GATHERING_SCHEDULE_INVALID_END_AT)
			}
			validateEarlyBird(earlyBirdDiscountRate, earlyBirdCapacity, maxParticipants)
			// 남/녀 정원은 모임 정원의 절반으로 정한다.
			val genderCapacity: Int = maxParticipants / 2
			return GatheringSchedule(
				gatheringId = gatheringId,
				startAt = startAt,
				endAt = endAt,
				fee = fee,
				maleCapacity = genderCapacity,
				femaleCapacity = genderCapacity,
				earlyBirdDiscountRate = earlyBirdDiscountRate,
				earlyBirdCapacity = earlyBirdCapacity,
				discountFee = discountFee,
				products = GatheringProducts.create(
					gatheringId = gatheringId,
					fee = fee,
					earlyBirdDiscountRate = earlyBirdDiscountRate,
					discountFee = discountFee,
				),
			)
		}

		// 얼리버드 할인율과 적용 인원은 세트다: 둘 다 있거나 둘 다 없어야 한다.
		// 있으면 할인율은 1..100(%), 인원은 1..모임 정원 범위여야 한다.
		private fun validateEarlyBird(
			earlyBirdDiscountRate: Int?,
			earlyBirdCapacity: Int?,
			maxParticipants: Int,
		) {
			if ((earlyBirdDiscountRate == null) != (earlyBirdCapacity == null)) {
				throw AdminException(AdminErrorCode.GATHERING_INVALID_EARLY_BIRD_CAPACITY)
			}
			if (earlyBirdDiscountRate != null && (earlyBirdDiscountRate < 1 || earlyBirdDiscountRate > 100)) {
				throw AdminException(AdminErrorCode.GATHERING_INVALID_EARLY_BIRD_DISCOUNT_RATE)
			}
			if (earlyBirdCapacity != null && (earlyBirdCapacity < 1 || earlyBirdCapacity > maxParticipants)) {
				throw AdminException(AdminErrorCode.GATHERING_INVALID_EARLY_BIRD_CAPACITY)
			}
		}
	}
}
