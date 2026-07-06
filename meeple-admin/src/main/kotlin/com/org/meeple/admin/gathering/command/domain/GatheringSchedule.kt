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
 */
data class GatheringSchedule(
	val id: Long = 0,
	val gatheringId: Long,
	val startAt: LocalDateTime,
	val endAt: LocalDateTime?,
	// 생성 직후는 예정(SCHEDULED). 시작/종료/취소로 전이한다.
	val status: GatheringScheduleStatus = GatheringScheduleStatus.SCHEDULED,
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
		 * [gatheringId] 모임에 [startAt]~[endAt] 시간 범위의 일정을 만든다.
		 * [startAt]은 [now] 이후여야 하고, [endAt]이 있으면 [startAt] 이후여야 한다. 예정(SCHEDULED)으로 생성한다.
		 */
		fun create(
			gatheringId: Long,
			startAt: LocalDateTime,
			endAt: LocalDateTime?,
			now: LocalDateTime,
		): GatheringSchedule {
			if (!startAt.isAfter(now)) {
				throw AdminException(AdminErrorCode.GATHERING_SCHEDULE_INVALID_START_AT)
			}
			if (endAt != null && !endAt.isAfter(startAt)) {
				throw AdminException(AdminErrorCode.GATHERING_SCHEDULE_INVALID_END_AT)
			}
			return GatheringSchedule(
				gatheringId = gatheringId,
				startAt = startAt,
				endAt = endAt,
			)
		}
	}
}
