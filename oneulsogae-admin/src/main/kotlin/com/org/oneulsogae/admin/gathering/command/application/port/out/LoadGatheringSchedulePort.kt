package com.org.oneulsogae.admin.gathering.command.application.port.out

import com.org.oneulsogae.admin.gathering.command.domain.GatheringSchedule

/** 모임 일정 로드 out-port. 없거나 soft-delete면 null. infra 어댑터가 구현한다. */
fun interface LoadGatheringSchedulePort {

	fun loadById(id: Long): GatheringSchedule?
}
