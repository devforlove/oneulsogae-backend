package com.org.meeple.api.admin.response

import com.org.meeple.admin.gathering.command.domain.GatheringSchedule

data class CreateGatheringScheduleResponse(
	val scheduleId: Long,
) {
	companion object {
		fun of(schedule: GatheringSchedule): CreateGatheringScheduleResponse =
			CreateGatheringScheduleResponse(scheduleId = schedule.id)
	}
}
