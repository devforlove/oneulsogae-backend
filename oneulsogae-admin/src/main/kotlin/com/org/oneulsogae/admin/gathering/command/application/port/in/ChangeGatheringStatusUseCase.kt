package com.org.oneulsogae.admin.gathering.command.application.port.`in`

import com.org.oneulsogae.common.gathering.GatheringStatus

/** 어드민 모임 상태 변경 유스케이스. 활성화(→RECRUITING)·취소(→CANCELED)를 하나로 처리한다. */
interface ChangeGatheringStatusUseCase {

	/** [id] 모임을 [status]로 전이한다. 없으면 GATHERING_NOT_FOUND, 전이 불가면 GATHERING_INVALID_STATUS_TRANSITION. */
	fun changeStatus(id: Long, status: GatheringStatus)
}
