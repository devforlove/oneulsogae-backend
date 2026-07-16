package com.org.meeple.admin.gathering.query.dao

import com.org.meeple.admin.gathering.query.dto.AdminGatheringMemberViews

/** 어드민 일정별 참가 신청 목록 조회 dao. infra가 구현한다. */
interface GetAdminGatheringMemberDao {

	/** [scheduleId] 일정의 참가 신청을 신청 순(id 오름차순)으로 조회한다. */
	fun findByScheduleId(scheduleId: Long): AdminGatheringMemberViews
}
