package com.org.meeple.admin.gathering.command.application.port.out

import com.org.meeple.admin.gathering.command.domain.AdminGathering

/** 수정 대상 모임을 전체 로드하는 out-port. 없거나 soft-delete면 null. */
fun interface LoadAdminGatheringPort {
	fun loadById(id: Long): AdminGathering?
}
