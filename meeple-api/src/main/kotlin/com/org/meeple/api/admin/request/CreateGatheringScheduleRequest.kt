package com.org.meeple.api.admin.request

import com.org.meeple.admin.gathering.command.application.port.`in`.command.CreateGatheringScheduleCommand
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

/**
 * 모임 일정 생성 요청. [startAt]은 필수, [endAt]은 선택(미정 가능)이다.
 * 시간 규칙(시작은 현재 이후, 종료는 시작 이후)은 도메인이 검증한다.
 */
data class CreateGatheringScheduleRequest(
	@field:NotNull(message = "일정 시작 시각은 필수입니다.")
	val startAt: LocalDateTime? = null,
	val endAt: LocalDateTime? = null,
) {
	fun toCommand(gatheringId: Long): CreateGatheringScheduleCommand =
		CreateGatheringScheduleCommand(
			gatheringId = gatheringId,
			startAt = startAt!!,
			endAt = endAt,
		)
}
