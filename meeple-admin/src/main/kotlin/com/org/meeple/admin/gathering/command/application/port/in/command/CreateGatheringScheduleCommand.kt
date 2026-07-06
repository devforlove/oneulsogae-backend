package com.org.meeple.admin.gathering.command.application.port.`in`.command

import java.time.LocalDateTime

/**
 * 모임 일정 생성 입력. [gatheringId] 모임에 [startAt]~[endAt] 시간 범위의 일정을 추가한다.
 * ([endAt]이 없으면 종료 시각 미정 일정)
 */
data class CreateGatheringScheduleCommand(
	val gatheringId: Long,
	val startAt: LocalDateTime,
	val endAt: LocalDateTime?,
)
