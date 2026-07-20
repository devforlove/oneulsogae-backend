package com.org.oneulsogae.admin.gathering.command.application.port.out

import com.org.oneulsogae.admin.gathering.command.domain.AdminGatheringMember

/** 참가 신청 행을 조회하는 아웃포트. */
interface LoadAdminGatheringMemberPort {

	fun loadById(memberId: Long): AdminGatheringMember?
}
