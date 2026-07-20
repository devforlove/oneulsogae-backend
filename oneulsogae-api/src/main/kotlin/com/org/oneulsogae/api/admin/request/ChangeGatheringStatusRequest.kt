package com.org.oneulsogae.api.admin.request

import com.org.oneulsogae.common.gathering.GatheringStatus
import jakarta.validation.constraints.NotNull

/**
 * 어드민 모임 상태 변경 요청. 전이할 목표 상태를 담는다.
 * CANCELED(취소)만 지원한다. (모임은 생성 시 이미 활성화이므로 그 외 값은 전이 규칙에서 거부)
 */
data class ChangeGatheringStatusRequest(
	@field:NotNull(message = "변경할 상태는 필수입니다.")
	val status: GatheringStatus? = null,
)
