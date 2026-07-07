package com.org.meeple.admin.gathering.command.application.port.`in`

import com.org.meeple.admin.gathering.command.application.port.`in`.command.CreateGatheringScheduleCommand
import com.org.meeple.admin.gathering.command.domain.GatheringSchedule

/** 모임 일정 생성 유스케이스. */
interface CreateGatheringScheduleUseCase {

	/** [command] 내용으로 일정을 생성·저장하고 저장된 일정을 반환한다. 대상 모임이 없으면 GATHERING_NOT_FOUND. */
	fun create(command: CreateGatheringScheduleCommand): GatheringSchedule
}
