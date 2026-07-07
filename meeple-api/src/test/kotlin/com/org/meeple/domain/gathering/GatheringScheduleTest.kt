package com.org.meeple.domain.gathering

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.gathering.command.domain.GatheringFee
import com.org.meeple.admin.gathering.command.domain.GatheringSchedule
import com.org.meeple.common.gathering.GatheringScheduleStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class GatheringScheduleTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0, 0)
	val start: LocalDateTime = now.plusDays(1)
	val end: LocalDateTime = start.plusHours(2)
	val fee: GatheringFee = GatheringFee(male = 10000, female = 8000)
	val earlyBirdRate: Int = 30
	val cap: Int = 4
	val maxParticipants: Int = 4

	describe("GatheringSchedule.create") {

		it("정상 입력이면 예정(SCHEDULED) 상태로 생성되고 정원은 모임 정원의 절반이다") {
			val schedule: GatheringSchedule = GatheringSchedule.create(
				gatheringId = 1L,
				startAt = start,
				endAt = end,
				fee = fee,
				earlyBirdDiscountRate = earlyBirdRate,
				earlyBirdCapacity = 2,
				discountFee = null,
				maxParticipants = maxParticipants,
				now = now,
			)

			schedule.status shouldBe GatheringScheduleStatus.SCHEDULED
			schedule.gatheringId shouldBe 1L
			schedule.startAt shouldBe start
			schedule.endAt shouldBe end
			schedule.fee shouldBe fee
			// 남/녀 정원은 모임 정원(4)의 절반(2).
			schedule.maleCapacity shouldBe 2
			schedule.femaleCapacity shouldBe 2
			schedule.earlyBirdDiscountRate shouldBe earlyBirdRate
			schedule.earlyBirdCapacity shouldBe 2
		}

		it("모임 정원이 홀수면 정수 나눗셈으로 내림한다") {
			val schedule: GatheringSchedule = GatheringSchedule.create(
				gatheringId = 1L,
				startAt = start,
				endAt = null,
				fee = fee,
				earlyBirdDiscountRate = null,
				earlyBirdCapacity = null,
				discountFee = null,
				maxParticipants = 5,
				now = now,
			)

			schedule.maleCapacity shouldBe 2
			schedule.femaleCapacity shouldBe 2
		}

		it("시작 시각이 현재 이전이면 GATHERING_SCHEDULE_INVALID_START_AT을 던진다") {
			shouldThrow<AdminException> {
				GatheringSchedule.create(1L, now.minusMinutes(1), null, fee, null, null, null, maxParticipants, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_SCHEDULE_INVALID_START_AT
		}

		it("시작 시각이 현재와 같으면 GATHERING_SCHEDULE_INVALID_START_AT을 던진다") {
			shouldThrow<AdminException> {
				GatheringSchedule.create(1L, now, null, fee, null, null, null, maxParticipants, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_SCHEDULE_INVALID_START_AT
		}

		it("종료 시각이 시작 시각 이전이면 GATHERING_SCHEDULE_INVALID_END_AT을 던진다") {
			shouldThrow<AdminException> {
				GatheringSchedule.create(1L, start, start.minusMinutes(1), fee, null, null, null, maxParticipants, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_SCHEDULE_INVALID_END_AT
		}

		it("종료 시각이 시작 시각과 같으면 GATHERING_SCHEDULE_INVALID_END_AT을 던진다") {
			shouldThrow<AdminException> {
				GatheringSchedule.create(1L, start, start, fee, null, null, null, maxParticipants, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_SCHEDULE_INVALID_END_AT
		}

		it("얼리버드 할인율은 있는데 적용 인원이 없으면 GATHERING_INVALID_EARLY_BIRD_CAPACITY를 던진다") {
			shouldThrow<AdminException> {
				GatheringSchedule.create(1L, start, end, fee, earlyBirdRate, null, null, maxParticipants, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_EARLY_BIRD_CAPACITY
		}

		it("얼리버드 적용 인원은 있는데 할인율이 없으면 GATHERING_INVALID_EARLY_BIRD_CAPACITY를 던진다") {
			shouldThrow<AdminException> {
				GatheringSchedule.create(1L, start, end, fee, null, 2, null, maxParticipants, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_EARLY_BIRD_CAPACITY
		}

		it("얼리버드 할인율이 0 이하이면 GATHERING_INVALID_EARLY_BIRD_DISCOUNT_RATE를 던진다") {
			shouldThrow<AdminException> {
				GatheringSchedule.create(1L, start, end, fee, 0, 2, null, maxParticipants, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_EARLY_BIRD_DISCOUNT_RATE
		}

		it("얼리버드 할인율이 100을 초과하면 GATHERING_INVALID_EARLY_BIRD_DISCOUNT_RATE를 던진다") {
			shouldThrow<AdminException> {
				GatheringSchedule.create(1L, start, end, fee, 101, 2, null, maxParticipants, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_EARLY_BIRD_DISCOUNT_RATE
		}

		it("얼리버드 적용 인원이 모임 정원을 초과하면 GATHERING_INVALID_EARLY_BIRD_CAPACITY를 던진다") {
			shouldThrow<AdminException> {
				GatheringSchedule.create(1L, start, end, fee, earlyBirdRate, maxParticipants + 1, null, maxParticipants, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_EARLY_BIRD_CAPACITY
		}

		it("얼리버드 적용 인원이 모임 정원과 같으면(경계값) 통과한다") {
			val schedule: GatheringSchedule = GatheringSchedule.create(
				1L, start, end, fee, earlyBirdRate, maxParticipants, null, maxParticipants, now,
			)

			schedule.earlyBirdCapacity shouldBe maxParticipants
		}
	}

	describe("GatheringSchedule.changeStatus") {

		fun scheduleWith(status: GatheringScheduleStatus): GatheringSchedule =
			GatheringSchedule(
				id = 1L, gatheringId = 1L, startAt = start, endAt = end,
				fee = fee, maleCapacity = cap, femaleCapacity = cap,
				earlyBirdDiscountRate = null, earlyBirdCapacity = null, discountFee = null, status = status,
			)

		it("예정 → 종료로 전이한다") {
			scheduleWith(GatheringScheduleStatus.SCHEDULED)
				.changeStatus(GatheringScheduleStatus.COMPLETED)
				.status shouldBe GatheringScheduleStatus.COMPLETED
		}

		it("예정 → 취소로 전이한다") {
			scheduleWith(GatheringScheduleStatus.SCHEDULED)
				.changeStatus(GatheringScheduleStatus.CANCELED)
				.status shouldBe GatheringScheduleStatus.CANCELED
		}

		it("종료된 일정은 전이할 수 없다") {
			shouldThrow<AdminException> {
				scheduleWith(GatheringScheduleStatus.COMPLETED).changeStatus(GatheringScheduleStatus.CANCELED)
			}.errorCode shouldBe AdminErrorCode.GATHERING_SCHEDULE_INVALID_STATUS_TRANSITION
		}

		it("취소된 일정은 전이할 수 없다") {
			shouldThrow<AdminException> {
				scheduleWith(GatheringScheduleStatus.CANCELED).changeStatus(GatheringScheduleStatus.COMPLETED)
			}.errorCode shouldBe AdminErrorCode.GATHERING_SCHEDULE_INVALID_STATUS_TRANSITION
		}
	}
})
