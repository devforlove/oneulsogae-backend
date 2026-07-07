package com.org.meeple.admin.gathering.command.application.port.`in`

import com.org.meeple.admin.gathering.command.application.port.`in`.command.CreateAdminGatheringCommand
import com.org.meeple.admin.gathering.command.domain.AdminGathering

/** 어드민 모임 생성 유스케이스. */
interface CreateAdminGatheringUseCase {

	/** [command] 내용으로 모임을 생성·저장하고 저장된 모임을 반환한다. */
	fun create(command: CreateAdminGatheringCommand): AdminGathering
}
