package com.org.oneulsogae.core.gathering.command.application.port.out

import com.org.oneulsogae.core.gathering.command.domain.GatheringMember

/** (schedule, user)의 기존 참가 행을 조회하는 아웃포트. (중복 신청 검증·거절 행 되살림용) */
interface LoadGatheringMemberPort {

	fun loadByScheduleIdAndUserId(scheduleId: Long, userId: Long): GatheringMember?
}
