package com.org.oneulsogae.admin.gathering.command.application.port.out

import com.org.oneulsogae.admin.gathering.command.domain.GatheringSchedule

/** 모임 일정 저장 out-port. infra 어댑터가 구현한다. */
fun interface SaveGatheringSchedulePort {

	fun save(schedule: GatheringSchedule): GatheringSchedule
}
