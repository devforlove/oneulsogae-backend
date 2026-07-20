package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.gathering.command.domain.GatheringSchedule

data class CreateGatheringScheduleResponse(
	val scheduleId: Long,
) {
	companion object {
		fun of(schedule: GatheringSchedule): CreateGatheringScheduleResponse =
			CreateGatheringScheduleResponse(scheduleId = schedule.id)
	}
}
