package com.org.oneulsogae.admin.gathering.command.application.port.out

import com.org.oneulsogae.common.gathering.GatheringMemberStatus

/** 참가 신청 상태를 전이하는 아웃포트. */
interface ChangeGatheringMemberStatusPort {

	fun changeStatus(memberId: Long, status: GatheringMemberStatus)
}
