package com.org.oneulsogae.admin.gathering.command.application.port.`in`

/** 참가 신청 거절 인포트(유스케이스). 승인대기(PENDING) → 거절(REJECTED) + 일정 여분 복원. */
interface RejectGatheringMemberUseCase {

	fun reject(scheduleId: Long, memberId: Long)
}
