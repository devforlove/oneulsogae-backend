package com.org.meeple.admin.gathering.query.service.port.`in`

import com.org.meeple.admin.gathering.query.dto.AdminGatheringMemberDetailView
import com.org.meeple.admin.gathering.query.dto.AdminGatheringMemberPage
import com.org.meeple.common.gathering.GatheringMemberStatus

/** 어드민 일정별 참가 신청 조회 인포트(유스케이스). */
interface GetAdminGatheringMembersUseCase {

	/** [scheduleId] 일정의 참가 신청을 [status](생략 시 전체) 필터로 [page](0부터)·[size] 페이징 조회한다. */
	fun getMembers(scheduleId: Long, page: Int, size: Int, status: GatheringMemberStatus?): AdminGatheringMemberPage

	/** [scheduleId] 일정의 참가 신청([memberId]) 상세(유저 모임 프로필)를 조회한다. 신청이 없으면 예외를 던진다. */
	fun getMemberProfile(scheduleId: Long, memberId: Long): AdminGatheringMemberDetailView
}
