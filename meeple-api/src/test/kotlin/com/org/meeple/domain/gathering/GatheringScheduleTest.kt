package com.org.meeple.domain.gathering

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
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

	describe("GatheringSchedule.create") {

		it("정상 입력이면 예정(SCHEDULED) 상태로 생성된다") {
			val schedule: GatheringSchedule = GatheringSchedule.create(
				gatheringId = 1L,
				startAt = start,
				endAt = end,
				now = now,
			)

			schedule.status shouldBe GatheringScheduleStatus.SCHEDULED
			schedule.gatheringId shouldBe 1L
			schedule.startAt shouldBe start
			schedule.endAt shouldBe end
		}

		it("종료 시각이 없어도 생성된다") {
			val schedule: GatheringSchedule = GatheringSchedule.create(
				gatheringId = 1L,
				startAt = start,
				endAt = null,
				now = now,
			)

			schedule.endAt shouldBe null
		}

		it("시작 시각이 현재 이전이면 GATHERING_SCHEDULE_INVALID_START_AT을 던진다") {
			shouldThrow<AdminException> {
				GatheringSchedule.create(gatheringId = 1L, startAt = now.minusMinutes(1), endAt = null, now = now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_SCHEDULE_INVALID_START_AT
		}

		it("시작 시각이 현재와 같으면 GATHERING_SCHEDULE_INVALID_START_AT을 던진다") {
			shouldThrow<AdminException> {
				GatheringSchedule.create(gatheringId = 1L, startAt = now, endAt = null, now = now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_SCHEDULE_INVALID_START_AT
		}

		it("종료 시각이 시작 시각 이전이면 GATHERING_SCHEDULE_INVALID_END_AT을 던진다") {
			shouldThrow<AdminException> {
				GatheringSchedule.create(gatheringId = 1L, startAt = start, endAt = start.minusMinutes(1), now = now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_SCHEDULE_INVALID_END_AT
		}

		it("종료 시각이 시작 시각과 같으면 GATHERING_SCHEDULE_INVALID_END_AT을 던진다") {
			shouldThrow<AdminException> {
				GatheringSchedule.create(gatheringId = 1L, startAt = start, endAt = start, now = now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_SCHEDULE_INVALID_END_AT
		}
	}

	describe("GatheringSchedule.changeStatus") {

		fun scheduleWith(status: GatheringScheduleStatus): GatheringSchedule =
			GatheringSchedule(id = 1L, gatheringId = 1L, startAt = start, endAt = end, status = status)

		it("예정 → 진행중으로 전이한다") {
			scheduleWith(GatheringScheduleStatus.SCHEDULED)
				.changeStatus(GatheringScheduleStatus.ONGOING)
				.status shouldBe GatheringScheduleStatus.ONGOING
		}

		it("예정 → 취소로 전이한다") {
			scheduleWith(GatheringScheduleStatus.SCHEDULED)
				.changeStatus(GatheringScheduleStatus.CANCELED)
				.status shouldBe GatheringScheduleStatus.CANCELED
		}

		it("진행중 → 종료로 전이한다") {
			scheduleWith(GatheringScheduleStatus.ONGOING)
				.changeStatus(GatheringScheduleStatus.COMPLETED)
				.status shouldBe GatheringScheduleStatus.COMPLETED
		}

		it("진행중 → 취소로 전이한다") {
			scheduleWith(GatheringScheduleStatus.ONGOING)
				.changeStatus(GatheringScheduleStatus.CANCELED)
				.status shouldBe GatheringScheduleStatus.CANCELED
		}

		it("예정 → 종료(중간 단계 생략)는 전이할 수 없다") {
			shouldThrow<AdminException> {
				scheduleWith(GatheringScheduleStatus.SCHEDULED).changeStatus(GatheringScheduleStatus.COMPLETED)
			}.errorCode shouldBe AdminErrorCode.GATHERING_SCHEDULE_INVALID_STATUS_TRANSITION
		}

		it("종료된 일정은 전이할 수 없다") {
			shouldThrow<AdminException> {
				scheduleWith(GatheringScheduleStatus.COMPLETED).changeStatus(GatheringScheduleStatus.ONGOING)
			}.errorCode shouldBe AdminErrorCode.GATHERING_SCHEDULE_INVALID_STATUS_TRANSITION
		}

		it("취소된 일정은 전이할 수 없다") {
			shouldThrow<AdminException> {
				scheduleWith(GatheringScheduleStatus.CANCELED).changeStatus(GatheringScheduleStatus.ONGOING)
			}.errorCode shouldBe AdminErrorCode.GATHERING_SCHEDULE_INVALID_STATUS_TRANSITION
		}
	}
})
