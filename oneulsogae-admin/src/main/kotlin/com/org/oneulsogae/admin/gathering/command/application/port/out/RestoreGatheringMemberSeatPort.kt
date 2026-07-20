package com.org.oneulsogae.admin.gathering.command.application.port.out

import com.org.oneulsogae.common.user.Gender

/** 거절된 접수의 자리를 일정 여분에 복원하는 아웃포트. [earlyBirdApplied]가 true면 얼리버드 여분도 복원한다. */
interface RestoreGatheringMemberSeatPort {

	fun restore(scheduleId: Long, gender: Gender, earlyBirdApplied: Boolean)
}
