package com.org.meeple.admin.gathering.query.dao

import com.org.meeple.admin.gathering.query.dto.AdminGatheringMemberDetailView
import com.org.meeple.admin.gathering.query.dto.AdminGatheringMemberViews
import com.org.meeple.common.gathering.GatheringMemberStatus

/** 어드민 일정별 참가 신청 조회 dao. infra가 구현한다. */
interface GetAdminGatheringMemberDao {

	/**
	 * 참가 신청을 신청 순(id 오름차순)으로 [offset]부터 [limit]건 조회한다. [status] 생략 시 전체.
	 * [scheduleId]가 있으면 그 일정만, null이면 전역(모든 모임·일정) 조회다.
	 */
	fun findPage(scheduleId: Long?, offset: Long, limit: Int, status: GatheringMemberStatus?): AdminGatheringMemberViews

	/** 참가 신청 개수. [scheduleId] null이면 전역, [status] 생략 시 전체. (페이징 메타데이터 계산용) */
	fun count(scheduleId: Long?, status: GatheringMemberStatus?): Long

	/** [scheduleId] 일정의 참가 신청([memberId]) 유저 모임 프로필을 gathering_profile에서 조회한다. 신청이 없으면 null(프로필만 없으면 필드가 null인 뷰). */
	fun findMemberProfile(scheduleId: Long, memberId: Long): AdminGatheringMemberDetailView?
}
