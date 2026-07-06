package com.org.meeple.api.admin.request

import com.org.meeple.common.gathering.GatheringScheduleStatus
import jakarta.validation.constraints.NotNull

/**
 * 모임 일정 상태 변경 요청. 전이할 목표 상태를 담는다.
 * (전이 가능 여부는 도메인 전이 규칙이 판정한다)
 */
data class ChangeGatheringScheduleStatusRequest(
	@field:NotNull(message = "변경할 상태는 필수입니다.")
	val status: GatheringScheduleStatus? = null,
)
