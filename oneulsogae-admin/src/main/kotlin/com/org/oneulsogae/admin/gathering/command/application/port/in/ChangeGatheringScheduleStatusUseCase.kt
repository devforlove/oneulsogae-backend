package com.org.oneulsogae.admin.gathering.command.application.port.`in`

import com.org.oneulsogae.common.gathering.GatheringScheduleStatus

/** 모임 일정 상태 변경 유스케이스. */
interface ChangeGatheringScheduleStatusUseCase {

	/**
	 * [gatheringId] 모임의 [scheduleId] 일정 상태를 [status]로 전이한다.
	 * 일정이 없거나 해당 모임 소속이 아니면 GATHERING_SCHEDULE_NOT_FOUND, 전이 불가면 GATHERING_SCHEDULE_INVALID_STATUS_TRANSITION.
	 */
	fun changeStatus(gatheringId: Long, scheduleId: Long, status: GatheringScheduleStatus)
}
