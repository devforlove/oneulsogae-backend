package com.org.oneulsogae.admin.gathering.command.application.port.`in`

/** 참가 신청 승인 인포트(유스케이스). 승인대기(PENDING) → 참가(JOINED). */
interface ApproveGatheringMemberUseCase {

	fun approve(scheduleId: Long, memberId: Long)
}
