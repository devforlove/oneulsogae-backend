package com.org.oneulsogae.admin.gathering.command.application.port.out

import com.org.oneulsogae.admin.gathering.command.domain.AdminGatheringStatus

/** 상태 전이 대상 모임의 상태 로드 out-port. 없거나 soft-delete면 null. */
fun interface GetAdminGatheringPort {
	fun findById(id: Long): AdminGatheringStatus?
}
