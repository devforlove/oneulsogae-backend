package com.org.meeple.admin.gathering.command.application.port.out

import com.org.meeple.common.gathering.GatheringScheduleStatus

/** 모임 일정 상태 전이 저장 out-port. status를 [status]로 전이한다. infra 어댑터가 구현한다. */
fun interface ChangeGatheringScheduleStatusPort {
	fun changeStatus(id: Long, status: GatheringScheduleStatus)
}
