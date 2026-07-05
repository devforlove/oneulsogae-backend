package com.org.meeple.admin.gathering.command.application.port.`in`

/** 어드민 모임 활성화 유스케이스. 준비중(DRAFT) 모임을 모집중(RECRUITING)으로 전이한다. */
interface ActivateGatheringUseCase {

	/** [id] 모임을 활성화한다. 없으면 GATHERING_NOT_FOUND, 준비중이 아니면 GATHERING_NOT_ACTIVATABLE. */
	fun activate(id: Long)
}
