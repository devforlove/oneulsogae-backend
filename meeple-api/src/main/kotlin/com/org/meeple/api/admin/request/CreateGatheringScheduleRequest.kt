package com.org.meeple.api.admin.request

import com.org.meeple.admin.gathering.command.application.port.`in`.command.CreateGatheringScheduleCommand
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.time.LocalDateTime

/**
 * 모임 일정 생성 요청. [startAt]은 필수, [endAt]은 선택(미정 가능)이다. 참가비도 일정별로 받는다:
 * 정상가([maleFee]·[femaleFee], 필수)·얼리버드 특가([earlyBirdMaleFee]·[earlyBirdFemaleFee]·[earlyBirdCapacity], 선택)·할인가([discountMaleFee]·[discountFemaleFee], 선택).
 * 시간 규칙(시작은 현재 이후, 종료는 시작 이후)과 얼리버드 인원(모임 정원 이하)은 도메인이 검증한다.
 */
data class CreateGatheringScheduleRequest(
	@field:NotNull(message = "일정 시작 시각은 필수입니다.")
	val startAt: LocalDateTime? = null,
	val endAt: LocalDateTime? = null,

	// 정상가(남/녀, 필수)
	@field:NotNull(message = "남성 참가비는 필수입니다.")
	@field:PositiveOrZero(message = "참가비는 0원 이상이어야 합니다.")
	val maleFee: Int? = null,

	@field:NotNull(message = "여성 참가비는 필수입니다.")
	@field:PositiveOrZero(message = "참가비는 0원 이상이어야 합니다.")
	val femaleFee: Int? = null,

	// 정원(남/녀, 필수) — 여분(남은 자리)은 저장 시 이 값으로 초기화된다
	@field:NotNull(message = "남성 정원은 필수입니다.")
	@field:PositiveOrZero(message = "정원은 0명 이상이어야 합니다.")
	val maleCapacity: Int? = null,

	@field:NotNull(message = "여성 정원은 필수입니다.")
	@field:PositiveOrZero(message = "정원은 0명 이상이어야 합니다.")
	val femaleCapacity: Int? = null,

	// 얼리버드 특가(남/녀, 선택 — 남/녀를 함께 입력한다)
	@field:PositiveOrZero(message = "참가비는 0원 이상이어야 합니다.")
	val earlyBirdMaleFee: Int? = null,

	@field:PositiveOrZero(message = "참가비는 0원 이상이어야 합니다.")
	val earlyBirdFemaleFee: Int? = null,

	@field:Min(value = 1, message = "얼리버드 적용 인원은 1명 이상이어야 합니다.")
	val earlyBirdCapacity: Int? = null,

	// 할인가(남/녀, 선택 — 남/녀를 함께 입력한다)
	@field:PositiveOrZero(message = "참가비는 0원 이상이어야 합니다.")
	val discountMaleFee: Int? = null,

	@field:PositiveOrZero(message = "참가비는 0원 이상이어야 합니다.")
	val discountFemaleFee: Int? = null,
) {
	fun toCommand(gatheringId: Long): CreateGatheringScheduleCommand =
		CreateGatheringScheduleCommand(
			gatheringId = gatheringId,
			startAt = startAt!!,
			endAt = endAt,
			maleFee = maleFee!!,
			femaleFee = femaleFee!!,
			maleCapacity = maleCapacity!!,
			femaleCapacity = femaleCapacity!!,
			earlyBirdMaleFee = earlyBirdMaleFee,
			earlyBirdFemaleFee = earlyBirdFemaleFee,
			earlyBirdCapacity = earlyBirdCapacity,
			discountMaleFee = discountMaleFee,
			discountFemaleFee = discountFemaleFee,
		)
}
