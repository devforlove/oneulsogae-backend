package com.org.oneulsogae.admin.gathering.command.application.port.`in`

import com.org.oneulsogae.admin.gathering.command.application.port.`in`.command.UpdateAdminGatheringCommand

/** 어드민 모임 전체 수정 유스케이스. */
interface UpdateAdminGatheringUseCase {

	/** [id] 모임의 전체 데이터를 [command]로 교체한다. 없으면 GATHERING_NOT_FOUND. */
	fun update(id: Long, command: UpdateAdminGatheringCommand)
}
