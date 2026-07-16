package com.org.meeple.admin.gathering.query.service.port.`in`

import com.org.meeple.admin.gathering.query.dto.AdminGatheringMemberViews

/** 어드민 일정별 참가 신청 목록 조회 인포트(유스케이스). */
interface GetAdminGatheringMembersUseCase {

	fun getByScheduleId(scheduleId: Long): AdminGatheringMemberViews
}
