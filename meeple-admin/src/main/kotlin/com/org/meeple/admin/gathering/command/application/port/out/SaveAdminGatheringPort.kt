package com.org.meeple.admin.gathering.command.application.port.out

import com.org.meeple.admin.gathering.command.domain.AdminGathering

/** 어드민 모임 저장 out-port. infra 어댑터가 구현한다. */
fun interface SaveAdminGatheringPort {

	fun save(gathering: AdminGathering): AdminGathering
}
